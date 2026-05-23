package prolab;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.stage.Stage;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.Random;

import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import java.io.InputStream;
import java.io.ByteArrayInputStream;
import javafx.stage.FileChooser;
import java.io.File;
import java.io.FileInputStream;

import org.mindrot.jbcrypt.BCrypt;
import java.util.Optional;

import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Tooltip;

import java.util.Map;
import java.util.HashMap;

import javafx.scene.chart.BarChart;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

public class ProLab extends Application {

    private String hashleSifre(String sifre) {
        return BCrypt.hashpw(sifre, BCrypt.gensalt());
    }

    private String girisYapanAdminTcNo;  // Adminin tc numarasını tutan değişken
    private Image adminProfilResmi = null;
    private ImageView adminFoto = null;

    // Veritabanı bağlantı bilgileri
    private static final String DB_URL = "jdbc:mysql://localhost:3306/kullanicidb";
    private static final String DB_USER = "root";
    private static final String DB_PASSWORD = "654321";

    private static Connection connection; // Veritabanı bağlantısı

    // Tarih formatı için sabiti tanımlayalım
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    // EmailSender inner class'ı (Konsol uygulamanızdan aynen alındı)
    public static class EmailSender {

        private static final String KULLANICI = "mkubraozturk37@gmail.com";
        private static final String SIFRE = "wgde sxca hvks akey"; // Uygulama şifresi (normal şifre çalışmaz)

        public static String randomSifreOlustur() {
            int uzunluk = 8;
            String karakterler = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
            StringBuilder sifre = new StringBuilder();
            Random rastgele = new Random();
            for (int i = 0; i < uzunluk; i++) {
                sifre.append(karakterler.charAt(rastgele.nextInt(karakterler.length())));
            }
            return sifre.toString();
        }

        public static void hastaKayitMailiGonder(String aliciEmail, String tcNo, String sifre) {
            Properties props = new Properties();
            props.put("mail.smtp.host", "smtp.gmail.com");
            props.put("mail.smtp.port", "587");
            props.put("mail.smtp.auth", "true");
            props.put("mail.smtp.starttls.enable", "true"); //TLS şifrelemesi

            Session session = Session.getInstance(props, new javax.mail.Authenticator() {
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(KULLANICI, SIFRE);
                }
            });
            try {
                Message message = new MimeMessage(session);
                message.setFrom(new InternetAddress(KULLANICI));
                message.setRecipients(Message.RecipientType.TO,
                        InternetAddress.parse(aliciEmail));
                message.setSubject("Hasta Kaydı Bilgileri");
                message.setText("Sayın Yeni Hastamız,\n\n"
                        + "Sisteme kaydınız başarıyla gerçekleştirilmiştir.\n"
                        + "TC Kimlik Numarınız: " + tcNo + "\n"
                        + "Geçici Şifreniz: " + sifre + "\n\n"              
                        + "Sağlıklı günler dileriz.");
                Transport.send(message);
                System.out.println("E-posta başarıyla gönderildi!");

            } catch (MessagingException e) {
                System.out.println("E-posta gönderme hatası: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    // SekerUyariSistemi inner class'ı (Konsol uygulamanızdan adaptasyon)
    public static class SekerUyariSistemi {

        // Belirtilerin varlığını kontrol eden yardımcı metod
        private static boolean hasBelirti(Connection conn, String hastaTcNo, List<String> belirtiler) throws SQLException {
            if (belirtiler == null || belirtiler.isEmpty()) {
                return true; // Eğer belirti listesi boşsa, koşul sağlanmış sayılır (SQL'deki EXISTS ile eşleşir)
            }
            String sql = "SELECT COUNT(*) FROM belirtiler WHERE hasta_tc_no = ? AND zaman_dilimi != 'gecersiz' AND belirti_adi IN (";
            for (int i = 0; i < belirtiler.size(); i++) {
                sql += "?";
                if (i < belirtiler.size() - 1) {
                    sql += ",";
                }
            }
            sql += ")";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, hastaTcNo);
                for (int i = 0; i < belirtiler.size(); i++) {
                    pstmt.setString(i + 2, belirtiler.get(i));
                }
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getInt(1) == belirtiler.size(); // Tüm belirtilerin eşleştiğini kontrol et
                    }
                }
            }
            return false;
        }

public static List<String> hastaSaglikDurumuKontrol(Connection conn, String hastaTcNo) throws SQLException {
    List<String> doktoraMesaj = new ArrayList<>();
    float ortalamaSekerDegeri = -1;
    int toplamOlcumSayisi = 0;

    // Ortalama şeker değerini al
    String sql = """
    SELECT AVG(seker_degeri) AS ortalama_seker, COUNT(*) AS toplam_olcum FROM (
        SELECT tarih, zaman_dilimi, AVG(seker_degeri) AS seker_degeri
        FROM kan_sekeri
        WHERE hasta_tc_no = ? 
          AND giris_turu = 'hasta' 
          AND zaman_dilimi != 'gecersiz'
        GROUP BY tarih, zaman_dilimi
    ) AS gunluk_tekil_olcumler
    """;

    try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
        pstmt.setString(1, hastaTcNo);
        try (ResultSet rs = pstmt.executeQuery()) {
            if (rs.next()) {
                Double ortalama = rs.getDouble("ortalama_seker");
                if (!rs.wasNull()) {
                    ortalamaSekerDegeri = ortalama.floatValue();
                }
                toplamOlcumSayisi = rs.getInt("toplam_olcum");

                System.out.println("Ortalama şeker: " + ortalamaSekerDegeri);
                System.out.println("Toplam ölçüm: " + toplamOlcumSayisi);
            }
        }
    } catch (SQLException e) {
        e.printStackTrace();
    }

    if (toplamOlcumSayisi <= 3) {
        doktoraMesaj.add("Hastanın toplam kan şekeri ölçüm sayısı yetersiz (<=3). Durum izlenmelidir.");
        return doktoraMesaj;
    }

    if (ortalamaSekerDegeri == -1) {
        doktoraMesaj.add("Hastanın kan şekeri değeri bulunmuyor veya ortalama hesaplanamadı.");
        return doktoraMesaj;
    }

    String sekerSeviyesi = "";
    String oneriler = "Diyet ve egzersiz önerisi yok.";
    String insulinOnerisi = "Yok";

    // Seviye belirleme
    if (ortalamaSekerDegeri < 70) {
        sekerSeviyesi = "Düşük Seviye (Hipoglisemi)";
        insulinOnerisi = "Yok";
    } else if (ortalamaSekerDegeri <= 110) {
        sekerSeviyesi = "Normal Seviye";
    } else if (ortalamaSekerDegeri <= 150) {
        sekerSeviyesi = "Orta Yüksek Seviye";
        insulinOnerisi = "1 ml";
    } else if (ortalamaSekerDegeri <= 200) {
        sekerSeviyesi = "Yüksek Seviye";
        insulinOnerisi = "2 ml";
    } else {
        sekerSeviyesi = "Çok Yüksek Seviye (Hiperglisemi)";
        insulinOnerisi = "3 ml";
    }

    doktoraMesaj.add("Ortalama Kan Şekeri: " + String.format("%.1f", ortalamaSekerDegeri) + " mg/dL - " + sekerSeviyesi);

    // Belirtilere göre öneri
    if (ortalamaSekerDegeri < 70) {
        if (hasBelirti(conn, hastaTcNo, Arrays.asList("Nöropati", "Polifaji", "Yorgunluk"))) {
            oneriler = "Dengeli Beslenme, Düzenli ve Hafif Egzersiz Önerilir";
        } else {
            oneriler = "Acil durum! Hastanın kan şekeri tehlikeli derecede düşük. Hızlı müdahale gerekebilir.";
        }
    } else if (ortalamaSekerDegeri < 110) {
        if (hasBelirti(conn, hastaTcNo, Arrays.asList("Yorgunluk", "Kilo Kaybı"))) {
            oneriler = "Az Şekerli Diyet ve Yürüyüş Egzersizi Önerilir";
        } else if (hasBelirti(conn, hastaTcNo, Arrays.asList("Polifaji", "Polidipsi"))) {
            oneriler = "Dengeli Beslenme, Düzenli ve Hafif Egzersiz Önerilir";
        } else {
            oneriler = "Kan şekeri normal aralıkta. Genel sağlıklı yaşam tarzına devam edilebilir.";
        }
    } else if (ortalamaSekerDegeri < 180) {
        if (hasBelirti(conn, hastaTcNo, Arrays.asList("Bulanık Görme", "Nöropati"))) {
            oneriler = "Az Şekerli Diyet ve Klinik Egzersiz Takibi Önerilir";
        } else if (hasBelirti(conn, hastaTcNo, Arrays.asList("Poliüri", "Polidipsi"))) {
            oneriler = "Şekersiz Diyet ve Klinik Egzersiz Takibi Önerilir";
        } else if (hasBelirti(conn, hastaTcNo, Arrays.asList("Yorgunluk", "Nöropati", "Bulanık Görme"))) {
            oneriler = "Az Şekerli Diyet ve Yürüyüş Egzersizi Önerilir";
        } else {
            oneriler = "Kan şekeri yüksek seyrediyor. Diyabet riskine karşı dikkatli olunmalı.";
        }
    } else {
        if (hasBelirti(conn, hastaTcNo, Arrays.asList("Yaraların Yavaş İyileşmesi", "Polifaji", "Polidipsi"))) {
            oneriler = "Şekersiz Diyet ve Klinik Egzersiz Takibi Önerilir";
        } else if (hasBelirti(conn, hastaTcNo, Arrays.asList("Yaraların Yavaş İyileşmesi", "Kilo Kaybı"))) {
            oneriler = "Şekersiz Diyet ve Yürüyüş Egzersizi Önerilir";
        } else {
            oneriler = "Acil durum! Kan şekeri tehlikeli derecede yüksek. Doktor kontrolü ve acil müdahale gerekebilir.";
        }
    }

    doktoraMesaj.add("Öneri: " + oneriler);
    doktoraMesaj.add("İnsülin Önerisi: " + insulinOnerisi);

    return doktoraMesaj;
}

        public static void uyarilariGoster(List<String> doktorMesajlari) {
            System.out.println("\n===== Doktora Mesajlar =====");
            if (doktorMesajlari.isEmpty()) {
                System.out.println("Herhangi bir doktor mesajı bulunmuyor.");
            } else {
                for (String mesaj : doktorMesajlari) {
                    System.out.println("- " + mesaj);
                }
            }
            System.out.println("============================");
        }
    }

    // Uygulama başlatıldığında çalışacak metod
    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("Hastane Otomasyonu - Giriş");

        try {
            // Veritabanı bağlantısını kur
            connection = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
            System.out.println("Veritabanına başarıyla bağlanıldı!");
            // İlk çalıştırmada tablo kurulumu (eğer yoksa)
            veritabanıKurulumu(connection); // Bu metodu kendi DatabaseManager sınıfınızdan çağırın
        } catch (SQLException e) {
            showAlert(Alert.AlertType.ERROR, "Veritabanı Hatası", "Veritabanı bağlantısı kurulamadı: " + e.getMessage());
            e.printStackTrace();
            System.exit(1); // Bağlantı hatası durumunda uygulamayı kapat
            return;
        }

        girisEkraniGoster(primaryStage);
    }

    // Giriş Ekranı
    private void girisEkraniGoster(Stage primaryStage) {
        GridPane grid = new GridPane();
        grid.setAlignment(Pos.CENTER);
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(25, 25, 25, 25));

        Text sceneTitle = new Text("Hoş Geldiniz");
        sceneTitle.setFont(Font.font("Tahoma", FontWeight.NORMAL, 20));
        grid.add(sceneTitle, 0, 0, 2, 1);

        Label userName = new Label("TC No:");
        grid.add(userName, 0, 1);

        TextField userTextField = new TextField();
        grid.add(userTextField, 1, 1);

        Label pw = new Label("Şifre:");
        grid.add(pw, 0, 2);

        PasswordField pwBox = new PasswordField();
        grid.add(pwBox, 1, 2);

        Button loginButton = new Button("Giriş Yap");
        HBox hbBtn = new HBox(10);
        hbBtn.setAlignment(Pos.BOTTOM_RIGHT);
        hbBtn.getChildren().add(loginButton);
        grid.add(hbBtn, 1, 4);

        final Text actiontarget = new Text();
        grid.add(actiontarget, 1, 6);

        loginButton.setOnAction(e -> {
            String tcNo = userTextField.getText();
            String password = pwBox.getText();

            try {
                if (girisYap(connection, tcNo, password, "admin")) {
                    actiontarget.setFill(Color.GREEN);
                    actiontarget.setText("Admin olarak giriş yapıldı!");
                    adminPaneliEkrani(primaryStage, tcNo); // Admin ekranını göster
                } else if (girisYap(connection, tcNo, password, "kullanicilar")) {
                    actiontarget.setFill(Color.GREEN);
                    actiontarget.setText("Hasta olarak giriş yapıldı!");
                    hastaPaneli(primaryStage, tcNo); // Hasta ekranını göster
                } else {
                    actiontarget.setFill(Color.FIREBRICK);
                    actiontarget.setText("Giriş başarısız. Lütfen kullanıcı adı (TC No) ve şifrenizi kontrol edin.");
                }
            } catch (SQLException ex) {
                showAlert(Alert.AlertType.ERROR, "Giriş Hatası", "Veritabanı hatası oluştu: " + ex.getMessage());
                ex.printStackTrace();
            }
        });

        Scene scene = new Scene(grid, 400, 300);
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    // Uygulama kapatıldığında veritabanı bağlantısını kapatır
    @Override
    public void stop() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                System.out.println("Veritabanı bağlantısı kapatıldı.");
            }
        } catch (SQLException e) {
            System.err.println("Veritabanı bağlantısını kapatırken hata oluştu: " + e.getMessage());
        }
    }

    // --- Yardımcı Metotlar (Konsol uygulamanızdan adaptasyon) ---
    // Bu metodu kendi veritabanı kurulum mantığınıza göre doldurun
    private void veritabanıKurulumu(Connection conn) {
        try {
            String createAdminTableSql = "CREATE TABLE IF NOT EXISTS admin ("
                    + "tc_no VARCHAR(11) PRIMARY KEY, "
                    + "sifre VARCHAR(255))";
            try (PreparedStatement stmt = conn.prepareStatement(createAdminTableSql)) {
                stmt.executeUpdate();
            }

            String createKullanicilarTableSql = "CREATE TABLE IF NOT EXISTS kullanicilar ("
                    + "tc_no VARCHAR(11) PRIMARY KEY, "
                    + "sifre VARCHAR(255), "
                    + "ad VARCHAR(255), "
                    + "soyad VARCHAR(255), "
                    + "email VARCHAR(255), "
                    + "dogum_tarihi DATE, "
                    + "cinsiyet VARCHAR(50))";
            try (PreparedStatement stmt = conn.prepareStatement(createKullanicilarTableSql)) {
                stmt.executeUpdate();
            }

            // Belirtiler tablosunun varlığını kontrol et ve yoksa oluştur
            String checkBelirtilerSql = "SHOW TABLES LIKE 'belirtiler'";
            try (PreparedStatement stmt = conn.prepareStatement(checkBelirtilerSql); ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    String createBelirtilerSql = "CREATE TABLE IF NOT EXISTS belirtiler ("
                            + "id INT AUTO_INCREMENT PRIMARY KEY, "
                            + "hasta_tc_no VARCHAR(11), "
                            + "belirti_adi VARCHAR(255), "
                            + "aciklama TEXT, "
                            + "FOREIGN KEY (hasta_tc_no) REFERENCES kullanicilar(tc_no))";
                    try (PreparedStatement createStmt = conn.prepareStatement(createBelirtilerSql)) {
                        createStmt.executeUpdate();
                        System.out.println("Belirtiler tablosu oluşturuldu.");
                    }
                }
            }

            // Kan şekeri tablosunun varlığını kontrol et ve yoksa oluştur
            String checkKanSekeriSql = "SHOW TABLES LIKE 'kan_sekeri'";
            try (PreparedStatement stmt = conn.prepareStatement(checkKanSekeriSql); ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    String createKanSekeriSql = "CREATE TABLE IF NOT EXISTS kan_sekeri ("
                            + "id INT AUTO_INCREMENT PRIMARY KEY, "
                            + "hasta_tc_no VARCHAR(11), "
                            + "tarih DATE, "
                            + "saat TIME, "
                            + "seker_degeri FLOAT, "
                            + "FOREIGN KEY (hasta_tc_no) REFERENCES kullanicilar(tc_no))";
                    try (PreparedStatement createStmt = conn.prepareStatement(createKanSekeriSql)) {
                        createStmt.executeUpdate();
                        System.out.println("Kan şekeri tablosu oluşturuldu.");
                    }
                }
            }

            // Doktor Tanımlamaları tablosu (Egzersiz/Diyet)
            String createDoktorTanimlamalariSql = "CREATE TABLE IF NOT EXISTS doktor_tanimlamalari ("
                    + "id INT AUTO_INCREMENT PRIMARY KEY, "
                    + "hasta_tc_no VARCHAR(11) UNIQUE, "
                    + // Her hastanın tek bir tanımlaması olsun
                    "egzersiz_tanimi TEXT, "
                    + "diyet_tanimi TEXT, "
                    + "FOREIGN KEY (hasta_tc_no) REFERENCES kullanicilar(tc_no))";
            try (PreparedStatement stmt = conn.prepareStatement(createDoktorTanimlamalariSql)) {
                stmt.executeUpdate();
                System.out.println("doktor_tanimlamalari tablosu oluşturuldu.");
            }

            // Günlük Takip tablosu
            String createGunlukTakipSql = "CREATE TABLE IF NOT EXISTS gunluk_takip ("
                    + "id INT AUTO_INCREMENT PRIMARY KEY, "
                    + "hasta_tc_no VARCHAR(11), "
                    + "tarih DATE, "
                    + "egzersiz_durumu VARCHAR(50), "
                    + // "yapıldı" / "yapılmadı"
                    "diyet_durumu VARCHAR(50), "
                    + // "uygulandı" / "uygulanmadı"
                    "ek_belirtiler TEXT, "
                    + // Hastanın ekleyeceği belirtiler
                    "FOREIGN KEY (hasta_tc_no) REFERENCES kullanicilar(tc_no), "
                    + "UNIQUE (hasta_tc_no, tarih))"; // Bir hasta bir günde sadece bir giriş yapabilir
            try (PreparedStatement stmt = conn.prepareStatement(createGunlukTakipSql)) {
                stmt.executeUpdate();
                System.out.println("gunluk_takip tablosu oluşturuldu.");
            }

            System.out.println("Gerekli tablolar kontrol edildi/oluşturuldu ve örnek veriler eklendi.");
        } catch (SQLException e) {
            System.out.println("Veritabanı ayarlanırken hata: " + e.getMessage());
            e.printStackTrace();
        }

    }

    private boolean girisYap(Connection conn, String tcNo, String girilenSifre, String tabloAdi) throws SQLException {
        String sql = "SELECT sifre FROM " + tabloAdi + " WHERE tc_no = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, tcNo);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    String hashliSifre = rs.getString("sifre");
                    return BCrypt.checkpw(girilenSifre, hashliSifre);
                }
            }
        }
        return false;
    }

// Admin Paneli Ekranı
    private void adminPaneliEkrani(Stage primaryStage, String adminTcNo) {
        this.girisYapanAdminTcNo = adminTcNo;
        VBox adminLayout = new VBox(15);
        adminLayout.setAlignment(Pos.TOP_CENTER);
        adminLayout.setPadding(new Insets(20));

        Text title = new Text("Admin Paneli");
        title.setFont(Font.font("Arial", FontWeight.BOLD, 24));
        adminLayout.getChildren().add(title);

// Eğer adminFoto daha önce oluşturulmamışsa oluştur
        if (adminFoto == null) {
            adminFoto = new ImageView();
            adminFoto.setFitWidth(120);
            adminFoto.setFitHeight(120);
            adminFoto.setPreserveRatio(true);
        }

        // Resmi daha önce yüklenmediyse veritabanından çek
        if (adminProfilResmi == null) {
            try {
                String sql = "SELECT ad, soyad, profil_resmi FROM admin WHERE tc_no = ?";
                try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                    pstmt.setString(1, adminTcNo);
                    try (ResultSet rs = pstmt.executeQuery()) {
                        if (rs.next()) {
                            String ad = rs.getString("ad");
                            String soyad = rs.getString("soyad");

                            Label welcomeLabel = new Label("Hoş Geldiniz, Dr. " + ad + " " + soyad);
                            adminLayout.getChildren().add(welcomeLabel);

                            byte[] imageBytes = rs.getBytes("profil_resmi");
                            if (imageBytes != null) {
                                InputStream is = new ByteArrayInputStream(imageBytes);
                                adminProfilResmi = new Image(is);
                            }
                        }
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }

// Eğer resim yüklüyse imageView’a bağla
        if (adminProfilResmi != null) {
            adminFoto.setImage(adminProfilResmi);
        }

        adminLayout.getChildren().add(adminFoto);

        // 📋 Butonlar
        Button newPatientButton = new Button("Yeni Hasta Ekle");
        newPatientButton.setMaxWidth(Double.MAX_VALUE);
        newPatientButton.setOnAction(e -> YeniHastaEkle(primaryStage, girisYapanAdminTcNo));

        Button enterSugarLevelButton = new Button("Hasta Kan Şekeri Ölçümü Gir (Admin)");
        enterSugarLevelButton.setMaxWidth(Double.MAX_VALUE);
        enterSugarLevelButton.setOnAction(e -> AdminKanSekeriGiris(primaryStage));

        Button defineDoctorNotesButton = new Button("Hasta Egzersiz/Diyet Tanımla");
        defineDoctorNotesButton.setMaxWidth(Double.MAX_VALUE);
        defineDoctorNotesButton.setOnAction(e -> DoktorTanimlamaEkrani(primaryStage));

        Button viewPatientButton = new Button("Hasta Bilgilerini Görüntüle");
        Button filterPatientsButton = new Button("Hastaları Listele / Filtrele");
        filterPatientsButton.setMaxWidth(Double.MAX_VALUE);
        filterPatientsButton.setOnAction(e -> HastaFiltrelemeEkrani(primaryStage));
        adminLayout.getChildren().add(filterPatientsButton);

        viewPatientButton.setMaxWidth(Double.MAX_VALUE);
        viewPatientButton.setOnAction(e -> HastaBilgileriniGoruntule(primaryStage));

        Button logoutButton = new Button("Çıkış Yap");
        logoutButton.setMaxWidth(Double.MAX_VALUE);
        logoutButton.setOnAction(e -> girisEkraniGoster(primaryStage));

        Button grafikButton = new Button("Hasta Kan Şekeri Grafiği");
        grafikButton.setMaxWidth(Double.MAX_VALUE);
        grafikButton.setOnAction(e -> {
            TextInputDialog dialog = new TextInputDialog();
            dialog.setTitle("Hasta TC Girişi");
            dialog.setHeaderText("Grafiğini görmek istediğiniz hastanın TC Kimlik Numarasını girin:");
            dialog.setContentText("TC No:");

            dialog.showAndWait().ifPresent(tcNo -> HastaGrafikselTakip(primaryStage, tcNo));
        });
        
        Button belirtiEkleButton = new Button("Belirti Ekle");
        belirtiEkleButton.setMaxWidth(Double.MAX_VALUE);
        belirtiEkleButton.setOnAction(e -> {
            TextInputDialog dialog = new TextInputDialog();
            dialog.setTitle("Hasta TC Girişi");
            dialog.setHeaderText("Belirti eklemek istediğiniz hastanın TC Kimlik Numarasını girin:");
            dialog.setContentText("TC No:");

            dialog.showAndWait().ifPresent(tcNo -> {
                if (!tcNo.trim().isEmpty()) {
                    yeniHastaIcinBelirtiEkleme(primaryStage, tcNo.trim());
                }
            });
        });
        adminLayout.getChildren().add(belirtiEkleButton);

        Button uyumOranButton = new Button("Hasta Egzersiz/Diyet Uyum Grafiği");
        uyumOranButton.setMaxWidth(Double.MAX_VALUE);
        uyumOranButton.setOnAction(e -> {
            TextInputDialog dialog = new TextInputDialog();
            dialog.setTitle("Hasta TC Girişi");
            dialog.setHeaderText("Uyum oranını görmek istediğiniz hastanın TC Kimlik Numarasını girin:");
            dialog.setContentText("TC No:");

            dialog.showAndWait().ifPresent(tcNo -> HastaUygulamaOranGrafik(primaryStage, tcNo));
        });
        adminLayout.getChildren().add(uyumOranButton);
        adminLayout.getChildren().addAll(
                newPatientButton,
                enterSugarLevelButton,
                defineDoctorNotesButton,
                viewPatientButton,
                grafikButton,
                new Separator(),
                logoutButton
        );

        Scene adminScene = new Scene(adminLayout, 600, 500);
        primaryStage.setScene(adminScene);
        primaryStage.setTitle("Hastane Otomasyonu - Admin Paneli");
    }

    // Admin için Kan Şekeri Giriş Ekranı (Hasta seçimi ile)
    private void AdminKanSekeriGiris(Stage primaryStage) {
        GridPane grid = new GridPane();
        grid.setAlignment(Pos.CENTER);
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(25));

        Text title = new Text("Hasta Kan Şekeri Ölçümü Girişi (Admin)");
        title.setFont(Font.font("Tahoma", FontWeight.NORMAL, 20));
        grid.add(title, 0, 0, 2, 1);

        Label hastaTcLabel = new Label("Hasta TC Kimlik No:");
        grid.add(hastaTcLabel, 0, 1);
        TextField hastaTcField = new TextField();
        grid.add(hastaTcField, 1, 1);

        Label tarihLabel = new Label("Tarih (GG-AA-YYYY):");
        grid.add(tarihLabel, 0, 2);
        TextField tarihField = new TextField(LocalDate.now().format(DATE_FORMATTER));
        grid.add(tarihField, 1, 2);

        Label saatLabel = new Label("Saat (HH:MM:SS):");
        grid.add(saatLabel, 0, 3);
        TextField saatField = new TextField(LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))); // Şu anki saati varsayılan olarak ayarla
        saatField.setPromptText("Örn: 07:30:00");
        grid.add(saatField, 1, 3);

        Label degerLabel = new Label("Şeker Değeri (mg/dL):");
        grid.add(degerLabel, 0, 4);
        TextField degerField = new TextField();
        grid.add(degerField, 1, 4);

        Button kaydetButton = new Button("Kaydet");
        Button backButton = new Button("Geri");
        HBox hbButtons = new HBox(10);
        hbButtons.setAlignment(Pos.BOTTOM_RIGHT);
        hbButtons.getChildren().addAll(backButton, kaydetButton);
        grid.add(hbButtons, 1, 5);

        final Text messageText = new Text();
        grid.add(messageText, 1, 6);

        kaydetButton.setOnAction(e -> {
            String hastaTcNo = hastaTcField.getText();
            String olcumTarihiStr = tarihField.getText();
            String olcumSaatiStr = saatField.getText();
            String sekerDegeriStr = degerField.getText();

            if (hastaTcNo.isEmpty() || olcumTarihiStr.isEmpty() || olcumSaatiStr.isEmpty() || sekerDegeriStr.isEmpty()) {
                messageText.setFill(Color.RED);
                messageText.setText("Tüm alanları doldurunuz!");
                return;
            }

            // Hasta TC'sinin varlığını kontrol et
            if (!HastaVarMi(hastaTcNo)) {
                messageText.setFill(Color.RED);
                messageText.setText("Belirtilen TC Kimlik No'ya sahip hasta bulunamadı.");
                return;
            }

            // Tarih formatı kontrolü ve parse etme (DD-MM-YYYY)
            LocalDate olcumTarihi;
            try {
                olcumTarihi = LocalDate.parse(olcumTarihiStr, DATE_FORMATTER);
            } catch (DateTimeParseException ex) {
                messageText.setFill(Color.RED);
                messageText.setText("Tarih formatı hatalı. Lütfen GG-AA-YYYY formatında girin.");
                return;
            }

            // Saat formatı kontrolü
            LocalTime saat = LocalTime.parse(olcumSaatiStr);
            String zamanDilimi = zamanDilimiBelirle(saat);

            if (zamanDilimi.equals("gecersiz")) {
                messageText.setFill(Color.ORANGE);
                messageText.setText("Zaman dışı ölçüm kaydedildi. Ancak ortalamaya dahil edilmeyecek.");
            } else {
                messageText.setFill(Color.GREEN);
                messageText.setText("Geçerli zaman diliminde ölçüm yapıldı.");
            }

            try {
                float sekerDegeri = Float.parseFloat(sekerDegeriStr);

                if (zamanDilimi.equals("gecersiz")) {
                    showAlert(Alert.AlertType.WARNING, "Uyarı", "Zaman dışı ölçüm yapıldı. Ortalama hesaplamaya dahil edilmeyecek.");
                }
                String insertSekerSql = "INSERT INTO kan_sekeri (hasta_tc_no, tarih, saat, seker_degeri, giris_turu, zaman_dilimi) VALUES (?, ?, ?, ?, 'hasta', ?)";

                try (PreparedStatement sekerPstmt = connection.prepareStatement(insertSekerSql)) {
                    sekerPstmt.setString(1, hastaTcNo);
                    sekerPstmt.setObject(2, olcumTarihi); // LocalDate objesini doğrudan kaydedin
                    sekerPstmt.setString(3, olcumSaatiStr);
                    sekerPstmt.setFloat(4, sekerDegeri);
                    sekerPstmt.setString(5, zamanDilimi);

                    int sekerInserted = sekerPstmt.executeUpdate();
                    if (sekerInserted > 0) {
                        messageText.setFill(Color.GREEN);
                        messageText.setText("Kan şekeri değeri başarıyla kaydedildi.");
                        hastaTcField.clear();
                        tarihField.setText(LocalDate.now().format(DATE_FORMATTER)); // Tekrar bugünün tarihini ayarla
                        saatField.setText(LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))); // Tekrar şimdiki saati ayarla
                        degerField.clear();
                        showAlert(Alert.AlertType.INFORMATION, "Başarılı", "Kan şekeri değeri kaydedildi.");
                    } else {
                        messageText.setFill(Color.RED);
                        messageText.setText("Kan şekeri değeri kaydedilemedi.");
                    }
                }
            } catch (NumberFormatException ex) {
                messageText.setFill(Color.RED);
                messageText.setText("Geçersiz kan şekeri değeri girdiniz. Lütfen sayısal bir değer girin.");
            } catch (SQLException ex) {
                messageText.setFill(Color.RED);
                messageText.setText("Veritabanı hatası: " + ex.getMessage());
                showAlert(Alert.AlertType.ERROR, "Veritabanı Hatası", "Kan şekeri kaydedilirken hata: " + ex.getMessage());
            }
        });

        backButton.setOnAction(e -> adminPaneliEkrani(primaryStage, girisYapanAdminTcNo));

        Scene scene = new Scene(grid, 500, 450);
        primaryStage.setScene(scene);
        primaryStage.setTitle("Hastane Otomasyonu - Admin Kan Şekeri Girişi");
    }

    // Admin için Egzersiz/Diyet Tanımlama Ekranı
    private void DoktorTanimlamaEkrani(Stage primaryStage) {
        GridPane grid = new GridPane();
        grid.setAlignment(Pos.CENTER);
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(25));

        Text title = new Text("Hasta Egzersiz/Diyet Tanımla");
        title.setFont(Font.font("Tahoma", FontWeight.NORMAL, 20));
        grid.add(title, 0, 0, 2, 1);

        Label hastaTcLabel = new Label("Hasta TC Kimlik No:");
        grid.add(hastaTcLabel, 0, 1);
        TextField hastaTcField = new TextField();
        grid.add(hastaTcField, 1, 1);

        Label egzersizLabel = new Label("Egzersiz Tanımı:");
        grid.add(egzersizLabel, 0, 2);
        TextArea egzersizArea = new TextArea();
        egzersizArea.setPromptText("Örn: Her gün 30 dakika tempolu yürüyüş.");
        egzersizArea.setPrefRowCount(3);
        grid.add(egzersizArea, 1, 2);

        Label diyetLabel = new Label("Diyet Tanımı:");
        grid.add(diyetLabel, 0, 3);
        TextArea diyetArea = new TextArea();
        diyetArea.setPromptText("Örn: Şekerli gıdalardan kaçın, bol sebze ve meyve tüket.");
        diyetArea.setPrefRowCount(3);
        grid.add(diyetArea, 1, 3);

        Button kaydetButton = new Button("Kaydet");
        Button backButton = new Button("Geri");
        HBox hbButtons = new HBox(10);
        hbButtons.setAlignment(Pos.BOTTOM_RIGHT);
        hbButtons.getChildren().addAll(backButton, kaydetButton);
        grid.add(hbButtons, 1, 4);

        final Text messageText = new Text();
        grid.add(messageText, 1, 5);

        kaydetButton.setOnAction(e -> {
            String hastaTcNo = hastaTcField.getText();
            String egzersizTanimi = egzersizArea.getText();
            String diyetTanimi = diyetArea.getText();

            if (hastaTcNo.isEmpty() || (egzersizTanimi.isEmpty() && diyetTanimi.isEmpty())) {
                messageText.setFill(Color.RED);
                messageText.setText("Hasta TC No ve en az bir tanımlama alanı doldurulmalıdır!");
                return;
            }

            if (!HastaVarMi(hastaTcNo)) {
                messageText.setFill(Color.RED);
                messageText.setText("Belirtilen TC Kimlik No'ya sahip hasta bulunamadı.");
                return;
            }

            try {
                // INSERT...ON DUPLICATE KEY UPDATE ile hem ekleme hem güncelleme yapılır
                String sql = "INSERT INTO doktor_tanimlamalari (hasta_tc_no, egzersiz_tanimi, diyet_tanimi) VALUES (?, ?, ?) "
                        + "ON DUPLICATE KEY UPDATE egzersiz_tanimi = VALUES(egzersiz_tanimi), diyet_tanimi = VALUES(diyet_tanimi)";
                try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                    pstmt.setString(1, hastaTcNo);
                    pstmt.setString(2, egzersizTanimi);
                    pstmt.setString(3, diyetTanimi);

                    int rowsAffected = pstmt.executeUpdate();
                    if (rowsAffected > 0) {
                        messageText.setFill(Color.GREEN);
                        messageText.setText("Hasta tanımlamaları başarıyla kaydedildi/güncellendi.");
                        hastaTcField.clear();
                        egzersizArea.clear();
                        diyetArea.clear();
                        showAlert(Alert.AlertType.INFORMATION, "Başarılı", "Tanımlamalar kaydedildi.");
                    } else {
                        messageText.setFill(Color.RED);
                        messageText.setText("Tanımlamalar kaydedilemedi.");
                    }
                }
            } catch (SQLException ex) {
                messageText.setFill(Color.RED);
                messageText.setText("Veritabanı hatası: " + ex.getMessage());
                showAlert(Alert.AlertType.ERROR, "Veritabanı Hatası", "Tanımlamalar kaydedilirken hata: " + ex.getMessage());
            }
        });

        backButton.setOnAction(e -> adminPaneliEkrani(primaryStage, girisYapanAdminTcNo));

        Scene scene = new Scene(grid, 550, 500);
        primaryStage.setScene(scene);
        primaryStage.setTitle("Hastane Otomasyonu - Doktor Tanımlamaları");
    }

    // Hasta TC'sinin varlığını kontrol eden yardımcı metod
    private boolean HastaVarMi (String tcNo) {
        String sql = "SELECT COUNT(*) FROM kullanicilar WHERE tc_no = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, tcNo);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        } catch (SQLException e) {
            System.err.println("Hasta kontrolü sırasında hata: " + e.getMessage());
        }
        return false;
    }

    private void YeniHastaEkle(Stage primaryStage, String adminTcNo) {
        GridPane grid = new GridPane();
        grid.setAlignment(Pos.CENTER);
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(25));

        Text title = new Text("Yeni Hasta Ekleme");
        title.setFont(Font.font("Tahoma", FontWeight.NORMAL, 20));
        grid.add(title, 0, 0, 2, 1);

        Label adLabel = new Label("Ad:");
        TextField adField = new TextField();
        grid.add(adLabel, 0, 1);
        grid.add(adField, 1, 1);

        Label soyadLabel = new Label("Soyad:");
        TextField soyadField = new TextField();
        grid.add(soyadLabel, 0, 2);
        grid.add(soyadField, 1, 2);

        Label emailLabel = new Label("Email:");
        TextField emailField = new TextField();
        grid.add(emailLabel, 0, 3);
        grid.add(emailField, 1, 3);

        Label tcNoLabel = new Label("TC Kimlik No:");
        TextField tcNoField = new TextField();
        grid.add(tcNoLabel, 0, 4);
        grid.add(tcNoField, 1, 4);

        Label dogumTarihiLabel = new Label("Doğum Tarihi (GG-AA-YYYY):");
        TextField dogumTarihiField = new TextField();
        dogumTarihiField.setPromptText("Örn: 15-01-1990");
        grid.add(dogumTarihiLabel, 0, 5);
        grid.add(dogumTarihiField, 1, 5);

        Label cinsiyetLabel = new Label("Cinsiyet:");
        ComboBox<String> cinsiyetComboBox = new ComboBox<>();
        cinsiyetComboBox.getItems().addAll("Erkek", "Kadın");
        cinsiyetComboBox.setValue("Erkek");
        grid.add(cinsiyetLabel, 0, 6);
        grid.add(cinsiyetComboBox, 1, 6);

        // --- Fotoğraf Seçimi ---
        Label fotoLabel = new Label("Profil Fotoğrafı:");
        Button fotoSecButton = new Button("Fotoğraf Seç");
        Text fotoBilgiText = new Text("Henüz seçilmedi.");
        final File[] secilenDosya = new File[1]; // Seçilen dosya burada tutulacak

        fotoSecButton.setOnAction(e -> {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Profil Fotoğrafı Seç");
            fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Resim Dosyaları", "*.jpg", "*.jpeg", "*.png"));
            File file = fileChooser.showOpenDialog(primaryStage);
            if (file != null) {
                secilenDosya[0] = file;
                fotoBilgiText.setText(file.getName() + " seçildi.");
            }
        });

        grid.add(fotoLabel, 0, 7);
        grid.add(fotoSecButton, 1, 7);
        grid.add(fotoBilgiText, 1, 8);

        // --- Butonlar ---
        Button ekleButton = new Button("Hastayı Ekle");
        Button backButton = new Button("Geri");
        HBox hbButtons = new HBox(10);
        hbButtons.setAlignment(Pos.BOTTOM_RIGHT);
        hbButtons.getChildren().addAll(backButton, ekleButton);
        grid.add(hbButtons, 1, 9);

        final Text messageText = new Text();
        grid.add(messageText, 1, 10);

        ekleButton.setOnAction(e -> {
            String ad = adField.getText();
            String soyad = soyadField.getText();
            String email = emailField.getText();
            String tcNo = tcNoField.getText();
            String dogumTarihiStr = dogumTarihiField.getText();
            String cinsiyet = cinsiyetComboBox.getValue();

            if (ad.isEmpty() || soyad.isEmpty() || email.isEmpty() || tcNo.isEmpty() || dogumTarihiStr.isEmpty() || cinsiyet.isEmpty()) {
                messageText.setFill(Color.RED);
                messageText.setText("Tüm alanları doldurunuz!");
                return;
            }

            LocalDate dogumTarihi;
            try {
                dogumTarihi = LocalDate.parse(dogumTarihiStr, DATE_FORMATTER);
            } catch (DateTimeParseException ex) {
                messageText.setFill(Color.RED);
                messageText.setText("Doğum tarihi formatı hatalı. GG-AA-YYYY formatında girin.");
                return;
            }

            try {
                String randomSifre = EmailSender.randomSifreOlustur();
                String hashliSifre = hashleSifre(randomSifre);

                String insertSql = "INSERT INTO kullanicilar (ad, soyad, email, tc_no, sifre, dogum_tarihi, cinsiyet, profil_resmi) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
                try (PreparedStatement pstmt = connection.prepareStatement(insertSql)) {
                    pstmt.setString(1, ad);
                    pstmt.setString(2, soyad);
                    pstmt.setString(3, email);
                    pstmt.setString(4, tcNo);
                    pstmt.setString(5, hashliSifre); // hashli şifre kullanılmalı
                    pstmt.setObject(6, dogumTarihi);
                    pstmt.setString(7, cinsiyet);

                    if (secilenDosya[0] != null) {
                        FileInputStream fis = new FileInputStream(secilenDosya[0]);
                        pstmt.setBinaryStream(8, fis, (int) secilenDosya[0].length());
                    } else {
                        pstmt.setNull(8, java.sql.Types.BLOB);
                    }

                    int inserted = pstmt.executeUpdate();
                    if (inserted > 0) {
                        // HASTA EKLENDİYSE, admin_hasta tablosuna ilişki ekle
                        String ekleAdminHasta = "INSERT INTO admin_hasta (admin_tc_no, hasta_tc_no) VALUES (?, ?)";
                        PreparedStatement pstmtAdminHasta = connection.prepareStatement(ekleAdminHasta);
                        pstmtAdminHasta.setString(1, girisYapanAdminTcNo); // Adminin TC'si
                        pstmtAdminHasta.setString(2, tcNo);                // Eklenen hastanın TC'si
                        pstmtAdminHasta.executeUpdate();

                        messageText.setFill(Color.GREEN);
                        messageText.setText("Hasta eklendi!");
                        showAlert(Alert.AlertType.INFORMATION, "Başarılı", "Hasta eklendi.");

                        try {
                            EmailSender.hastaKayitMailiGonder(email, tcNo, randomSifre);
                            showAlert(Alert.AlertType.INFORMATION, "Başarılı", "E poasta başarıyla gönderildi");
                        } catch (Exception ex) {
                            showAlert(Alert.AlertType.WARNING, "E-posta Hatası", "E-posta gönderilemedi: " + ex.getMessage());
                        }

                        yeniHastaIcinBelirtiEkleme(primaryStage, tcNo);
                    } else {
                        messageText.setFill(Color.RED);
                        messageText.setText("Hasta eklenemedi.");
                    }
                }

            } catch (Exception ex) {
                messageText.setFill(Color.RED);
                messageText.setText("Hata: " + ex.getMessage());
                ex.printStackTrace();
            }
        });

        backButton.setOnAction(e -> adminPaneliEkrani(primaryStage, girisYapanAdminTcNo));

        Scene scene = new Scene(grid, 500, 600);
        primaryStage.setScene(scene);
        primaryStage.setTitle("Yeni Hasta Ekle");
    }

    // Belirti Ekleme Ekranı (Yeni hasta eklendikten sonra)
    private void yeniHastaIcinBelirtiEkleme(Stage primaryStage, String hastaTcNo) {
        VBox layout = new VBox(15);
        layout.setAlignment(Pos.CENTER);
        layout.setPadding(new Insets(20));

        Label title = new Label("Hasta İçin Belirti Ekle");
        title.setFont(Font.font("Arial", FontWeight.BOLD, 18));
        layout.getChildren().add(title);

        Label tcNoLabel = new Label("Hasta TC Kimlik No: " + hastaTcNo);
        layout.getChildren().add(tcNoLabel);

        TextField belirtiField = new TextField();
        belirtiField.setPromptText("Belirti adını girin");
        layout.getChildren().add(belirtiField);

        TextArea aciklamaArea = new TextArea();
        aciklamaArea.setPromptText("Belirti açıklamasını girin (isteğe bağlı)");
        aciklamaArea.setPrefRowCount(3);
        layout.getChildren().add(aciklamaArea);

        Button ekleButton = new Button("Belirtiyi Ekle");
        Button bitirButton = new Button("Bitir ve Admin Paneline Dön");
        HBox buttonBox = new HBox(10);
        buttonBox.setAlignment(Pos.CENTER);
        buttonBox.getChildren().addAll(ekleButton, bitirButton);
        layout.getChildren().add(buttonBox);

        Text messageText = new Text();
        layout.getChildren().add(messageText);

        ekleButton.setOnAction(e -> {
            String belirtiAdi = belirtiField.getText();
            String aciklama = aciklamaArea.getText();

            if (belirtiAdi.isEmpty()) {
                messageText.setFill(Color.RED);
                messageText.setText("Belirti adı boş olamaz!");
                return;
            }

            String insertBelirtiSql = "INSERT INTO belirtiler (hasta_tc_no, belirti_adi, aciklama) VALUES (?, ?, ?)";
            try (PreparedStatement pstmt = connection.prepareStatement(insertBelirtiSql)) {
                pstmt.setString(1, hastaTcNo);
                pstmt.setString(2, belirtiAdi);
                pstmt.setString(3, aciklama);

                int rowsInserted = pstmt.executeUpdate();
                if (rowsInserted > 0) {
                    messageText.setFill(Color.GREEN);
                    messageText.setText("Belirti başarıyla eklendi.");
                    belirtiField.clear();
                    aciklamaArea.clear();
                } else {
                    messageText.setFill(Color.RED);
                    messageText.setText("Belirti eklenemedi.");
                }
            } catch (SQLException ex) {
                messageText.setFill(Color.RED);
                messageText.setText("Belirti eklenirken SQL Hatası oluştu: " + ex.getMessage());
                showAlert(Alert.AlertType.ERROR, "Veritabanı Hatası", "Belirti eklenirken hata: " + ex.getMessage());
            }
        });

        bitirButton.setOnAction(e -> adminPaneliEkrani(primaryStage, girisYapanAdminTcNo));

        Scene scene = new Scene(layout, 450, 400);
        primaryStage.setScene(scene);
        primaryStage.setTitle("Hastane Otomasyonu - Belirti Ekle");
    }

    // Hasta Bilgilerini Görüntüle Ekranı
    private void HastaBilgileriniGoruntule(Stage primaryStage) {
        VBox layout = new VBox(15);
        layout.setAlignment(Pos.TOP_CENTER);
        layout.setPadding(new Insets(20));

        Text title = new Text("Hasta Bilgileri Görüntüleme");
        title.setFont(Font.font("Arial", FontWeight.BOLD, 20));
        layout.getChildren().add(title);

        HBox searchBox = new HBox(10);
        searchBox.setAlignment(Pos.CENTER);
        Label searchLabel = new Label("TC Kimlik No:");
        TextField searchField = new TextField();
        Button searchButton = new Button("Ara");
        searchBox.getChildren().addAll(searchLabel, searchField, searchButton);
        layout.getChildren().add(searchBox);

        ImageView hastaFoto = new ImageView();
        hastaFoto.setFitWidth(150);
        hastaFoto.setFitHeight(150);
        layout.getChildren().add(hastaFoto);

        TextArea displayArea = new TextArea();
        displayArea.setEditable(false);
        displayArea.setPrefRowCount(15);
        layout.getChildren().add(displayArea);

        searchButton.setOnAction(e -> {
            String hastaTcNo = searchField.getText();
            if (hastaTcNo.isEmpty()) {
                showAlert(Alert.AlertType.WARNING, "Uyarı", "Lütfen TC Kimlik No girin.");
                return;
            }

            // YETKİ KONTROLÜ EKLE
            if (!HastaAdminTarafindanEklenmisMi(girisYapanAdminTcNo, hastaTcNo)) {
                showAlert(Alert.AlertType.ERROR, "Erişim Reddedildi", "Bu hastanın bilgilerine erişim yetkiniz yok.");
                return;
            }

            try {
                displayArea.setText(HastaBilgileri(hastaTcNo));

                // Fotoğrafı çek ve göster
                String sql = "SELECT profil_resmi FROM kullanicilar WHERE tc_no = ?";
                try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                    pstmt.setString(1, hastaTcNo);
                    try (ResultSet rs = pstmt.executeQuery()) {
                        if (rs.next()) {
                            byte[] imageBytes = rs.getBytes("profil_resmi");
                            if (imageBytes != null) {
                                InputStream is = new ByteArrayInputStream(imageBytes);
                                Image img = new Image(is);
                                hastaFoto.setImage(img);
                            } else {
                                hastaFoto.setImage(null); // Fotoğraf yoksa temizle
                            }
                        }
                    }
                }
            } catch (SQLException ex) {
                showAlert(Alert.AlertType.ERROR, "Veritabanı Hatası", "Hasta bilgileri görüntülenirken hata oluştu: " + ex.getMessage());
                ex.printStackTrace();
            }
        });

        Button backButton = new Button("Geri");
        backButton.setOnAction(e -> adminPaneliEkrani(primaryStage, girisYapanAdminTcNo));
        layout.getChildren().add(backButton);

        Scene scene = new Scene(layout, 600, 700);
        primaryStage.setScene(scene);
        primaryStage.setTitle("Hastane Otomasyonu - Hasta Bilgileri");
    }

    private String HastaBilgileri(String hastaTcNo) throws SQLException {
        StringBuilder sb = new StringBuilder();

        String hastaSorgu = "SELECT * FROM kullanicilar WHERE tc_no = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(hastaSorgu)) {
            pstmt.setString(1, hastaTcNo);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    sb.append("===== HASTA BİLGİLERİ =====").append("\n");
                    sb.append("Ad Soyad: ").append(rs.getString("ad")).append(" ").append(rs.getString("soyad")).append("\n");
                    sb.append("TC Kimlik No: ").append(rs.getString("tc_no")).append("\n");
                    sb.append("Email: ").append(rs.getString("email")).append("\n");

                    // Doğum tarihini YYYY-MM-DD formatından GG-AA-YYYY formatına çevir
                    java.sql.Date sqlDate = rs.getDate("dogum_tarihi");
                    if (sqlDate != null) {
                        LocalDate localDate = sqlDate.toLocalDate();
                        sb.append("Doğum Tarihi: ").append(localDate.format(DATE_FORMATTER)).append("\n"); // Formatı kullan
                    } else {
                        sb.append("Doğum Tarihi: Bilinmiyor\n");
                    }

                    sb.append("Cinsiyet: ").append(rs.getString("cinsiyet")).append("\n");
                    sb.append("==========================").append("\n");

                    // Hasta belirtilerini göster
                    sb.append(HastaBelirtileri(hastaTcNo));

                    // Doktor tanımlamalarını göster
                    sb.append(DoktorTanimlamalari(hastaTcNo));

                    // Kan şekeri durumunu kontrol et ve uyarıları göster
                    List<String> uyarilar = SekerUyariSistemi.hastaSaglikDurumuKontrol(connection, hastaTcNo);
                    sb.append("\n===== DOKTORA MESAJLAR =====").append("\n");
                    if (uyarilar.isEmpty()) {
                        sb.append("Herhangi bir doktor mesajı bulunmuyor.").append("\n");
                    } else {
                        for (String mesaj : uyarilar) {
                            sb.append("- ").append(mesaj).append("\n");
                        }
                    }
                    sb.append("============================").append("\n");

                    // Kan şekeri ölçüm geçmişini göster
                    sb.append(KanSekeriGecmisi(hastaTcNo));

                    // Günlük takip geçmişini göster
                    sb.append(GunlukTakipGecmisi(hastaTcNo));

                    List<String> gunlukUyariListesi = gunlukUyarilariGetir(connection, hastaTcNo);
                    sb.append("\n===== GÜNLÜK UYARILAR =====\n");
                    if (gunlukUyariListesi.isEmpty()) {
                        sb.append("Herhangi bir kritik uyarı bulunmuyor.\n");
                    } else {
                        for (String uyar : gunlukUyariListesi) {
                            sb.append("- ").append(uyar).append("\n");
                        }
                    }
                    sb.append("===========================\n");

                } else {
                    sb.append("Bu TC Kimlik No'ya sahip bir hasta bulunamadı.").append("\n");
                }
            }
        }
        return sb.toString();
    }

    private String DoktorTanimlamalari(String hastaTcNo) throws SQLException {
        StringBuilder sb = new StringBuilder();
        String sql = "SELECT egzersiz_tanimi, diyet_tanimi FROM doktor_tanimlamalari WHERE hasta_tc_no = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, hastaTcNo);
            try (ResultSet rs = pstmt.executeQuery()) {
                sb.append("\n===== DOKTOR TANIMLAMALARI =====").append("\n");
                if (rs.next()) {
                    sb.append("Egzersiz Tanımı: ").append(rs.getString("egzersiz_tanimi")).append("\n");
                    sb.append("Diyet Tanımı: ").append(rs.getString("diyet_tanimi")).append("\n");
                } else {
                    sb.append("Bu hasta için doktor tarafından tanımlanmış egzersiz veya diyet bulunmamaktadır.").append("\n");
                }
                sb.append("==================================").append("\n");
            }
        }
        return sb.toString();
    }

    private String HastaBelirtileri(String hastaTcNo) throws SQLException {
        StringBuilder sb = new StringBuilder();
        String belirtiSorgu = "SELECT * FROM belirtiler WHERE hasta_tc_no = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(belirtiSorgu)) {
            pstmt.setString(1, hastaTcNo);
            try (ResultSet rs = pstmt.executeQuery()) {
                sb.append("\n===== HASTA BELİRTİLERİ =====").append("\n");
                boolean belirtiVar = false;
                while (rs.next()) {
                    belirtiVar = true;
                    sb.append("- ").append(rs.getString("belirti_adi")).append(": ").append(rs.getString("aciklama")).append("\n");
                }
                if (!belirtiVar) {
                    sb.append("Hasta için kaydedilmiş belirti bulunmamaktadır.").append("\n");
                }
                sb.append("============================").append("\n");
            }
        }
        return sb.toString();
    }

    private String KanSekeriGecmisi(String hastaTcNo) throws SQLException {
        StringBuilder sb = new StringBuilder();
        String kanSekeriSorgu = "SELECT * FROM kan_sekeri WHERE hasta_tc_no = ? ORDER BY tarih DESC, saat DESC LIMIT 10";
        try (PreparedStatement pstmt = connection.prepareStatement(kanSekeriSorgu)) {
            pstmt.setString(1, hastaTcNo);
            try (ResultSet rs = pstmt.executeQuery()) {
                sb.append("\n===== SON KAN ŞEKERİ ÖLÇÜMLERİ =====").append("\n");
                boolean olcumVar = false;

                while (rs.next()) {
                    olcumVar = true;
                    float sekerDegeri = rs.getFloat("seker_degeri");
                    // Tarihi dd-MM-yyyy formatına çevirerek göster
                    LocalDate olcumTarihi = rs.getDate("tarih").toLocalDate();
                    String formattedDate = olcumTarihi.format(DATE_FORMATTER);

                    String durum = "";

                    if (sekerDegeri < 70) {
                        durum = "HİPOGLİSEMİ RİSKİ";
                    } else if (sekerDegeri > 200) {
                        durum = "HİPERGLİSEMİ";
                    } else if (sekerDegeri >= 151 && sekerDegeri <= 200) {
                        durum = "YÜKSEK";
                    } else if (sekerDegeri >= 111 && sekerDegeri <= 150) {
                        durum = "ORTA YÜKSEK";
                    } else {
                        durum = "NORMAL";
                    }

                    sb.append(String.format("%s %s: %.1f mg/dL - %s%n",
                            formattedDate, // GG-AA-YYYY formatında tarih
                            rs.getString("saat"),
                            sekerDegeri,
                            durum));
                }
                if (!olcumVar) {
                    sb.append("Hasta için kaydedilmiş kan şekeri ölçümü bulunmamaktadır.").append("\n");
                }
                sb.append("===================================").append("\n");
            }
        }
        return sb.toString();
    }

    private String GunlukTakipGecmisi(String hastaTcNo) throws SQLException {
        StringBuilder sb = new StringBuilder();

        String sql = "SELECT tarih, egzersiz_durumu, diyet_durumu, ek_belirtiler "
                + "FROM gunluk_takip WHERE hasta_tc_no = ? ORDER BY tarih ASC";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, hastaTcNo);
            try (ResultSet rs = pstmt.executeQuery()) {
                sb.append("\n===== GÜNLÜK TAKİP GEÇMİŞİ =====\n");
                boolean takipVar = false;

                while (rs.next()) {
                    takipVar = true;
                    LocalDate tarih = rs.getDate("tarih").toLocalDate();
                    String egzersiz = rs.getString("egzersiz_durumu");
                    String diyet = rs.getString("diyet_durumu");
                    String ekBelirtiler = rs.getString("ek_belirtiler");

                    sb.append("Tarih: ").append(tarih.format(DATE_FORMATTER)).append("\n");
                    sb.append("  Egzersiz: ").append(egzersiz != null && !egzersiz.trim().isEmpty() ? egzersiz : "belirtilmemiş").append("\n");
                    sb.append("  Diyet: ").append(diyet != null && !diyet.trim().isEmpty() ? diyet : "belirtilmemiş").append("\n");

                    if (ekBelirtiler != null && !ekBelirtiler.trim().isEmpty()) {
                        sb.append("  Ek Belirtiler: ").append(ekBelirtiler).append("\n");
                    }

                    sb.append("-----------------------------\n");
                }

                if (!takipVar) {
                    sb.append("Bu hasta için günlük takip kaydı bulunmamaktadır.\n");
                }

                sb.append("================================\n");
            }
        }

        return sb.toString();
    }

    private void hastaPaneli(Stage primaryStage, String hastaTcKimlik) {
        VBox patientLayout = new VBox(15);
        patientLayout.setAlignment(Pos.TOP_CENTER);
        patientLayout.setPadding(new Insets(20));

        Text title = new Text("Hasta Paneli");
        title.setFont(Font.font("Arial", FontWeight.BOLD, 24));
        patientLayout.getChildren().add(title);

        // 📸 Hasta Fotoğrafı
        ImageView hastaProfilFoto = new ImageView();
        hastaProfilFoto.setFitWidth(120);
        hastaProfilFoto.setFitHeight(120);

        try {
            String sql = "SELECT ad, soyad, profil_resmi FROM kullanicilar WHERE tc_no = ?";
            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setString(1, hastaTcKimlik);
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        // Ad-Soyad gösterimi için (isteğe bağlı)
                        String ad = rs.getString("ad");
                        String soyad = rs.getString("soyad");

                        Label welcomeLabel = new Label("Hoş Geldiniz, " + ad + " " + soyad + "!");
                        patientLayout.getChildren().add(welcomeLabel);

                        byte[] imageBytes = rs.getBytes("profil_resmi");
                        if (imageBytes != null) {
                            InputStream is = new ByteArrayInputStream(imageBytes);
                            Image img = new Image(is);
                            hastaProfilFoto.setImage(img);
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        patientLayout.getChildren().add(hastaProfilFoto);

        // Menü Butonları
        Button viewDoctorNotesButton = new Button("Doktor Tanımlamalarını Görüntüle");
        viewDoctorNotesButton.setMaxWidth(Double.MAX_VALUE);
        viewDoctorNotesButton.setOnAction(e -> doktorTanimlamalari(primaryStage, hastaTcKimlik));

        Button dailyTrackingButton = new Button("Günlük Takip Girişi");
        dailyTrackingButton.setMaxWidth(Double.MAX_VALUE);
        dailyTrackingButton.setOnAction(e -> gunlukTakipGirisi(primaryStage, hastaTcKimlik));

        Button enterSugarButton = new Button("Kan Şekeri Ölçümü Gir");
        enterSugarButton.setMaxWidth(Double.MAX_VALUE);
        enterSugarButton.setOnAction(e -> KanSekeriGiris(primaryStage, hastaTcKimlik));

        Button viewHistoryButton = new Button("Kan Şekeri Geçmişi Görüntüle");
        viewHistoryButton.setMaxWidth(Double.MAX_VALUE);
        viewHistoryButton.setOnAction(e -> kanSekeriGecmisi(primaryStage, hastaTcKimlik));

        Button dailySugarStatsButton = new Button("Günlük Kan Şekeri ve Ortalama");
        dailySugarStatsButton.setMaxWidth(Double.MAX_VALUE);
        dailySugarStatsButton.setOnAction(e -> gunlukSekerIstatistik(primaryStage, hastaTcKimlik));

        Button logoutButton = new Button("Çıkış Yap");
        logoutButton.setMaxWidth(Double.MAX_VALUE);
        logoutButton.setOnAction(e -> girisEkraniGoster(primaryStage));

        Button uygulamaOranButton = new Button("Egzersiz/Diyet Uyum Oranları");
        uygulamaOranButton.setMaxWidth(Double.MAX_VALUE);
        uygulamaOranButton.setOnAction(e -> HastaUygulamaOranGrafikHasta(primaryStage, hastaTcKimlik));

        Button sekerGrafikButton = new Button("Günlük Kan Şekeri Grafiği");
        sekerGrafikButton.setMaxWidth(Double.MAX_VALUE);
        sekerGrafikButton.setOnAction(e -> HastaGunlukSekeriGrafik(primaryStage, hastaTcKimlik));

        Button insulinFiltreButton = new Button("İnsülin Kayıtlarını Tarihe Göre Göster");
        insulinFiltreButton.setMaxWidth(Double.MAX_VALUE);
        insulinFiltreButton.setOnAction(e -> InsulinKayitlari(primaryStage, hastaTcKimlik));
        patientLayout.getChildren().add(insulinFiltreButton);

        patientLayout.getChildren().addAll(
                viewDoctorNotesButton,
                dailyTrackingButton,
                enterSugarButton,
                viewHistoryButton,
                dailySugarStatsButton,
                uygulamaOranButton,
                sekerGrafikButton,
                new Separator(),
                logoutButton
        );

        Scene patientScene = new Scene(patientLayout, 600, 450);
        primaryStage.setScene(patientScene);
        primaryStage.setTitle("Hastane Otomasyonu - Hasta Paneli");
    }

    // Hasta için Doktor Tanımlamalarını Görüntüleme Ekranı
    private void doktorTanimlamalari(Stage primaryStage, String hastaTcKimlik) {
        VBox layout = new VBox(15);
        layout.setAlignment(Pos.TOP_CENTER);
        layout.setPadding(new Insets(20));

        Text title = new Text("Doktor Tanımlamaları");
        title.setFont(Font.font("Arial", FontWeight.BOLD, 20));
        layout.getChildren().add(title);

        TextArea notesArea = new TextArea();
        notesArea.setEditable(false);
        notesArea.setPrefRowCount(10);
        layout.getChildren().add(notesArea);

        try {
            notesArea.setText(DoktorTanimlamalari(hastaTcKimlik));
        } catch (SQLException ex) {
            notesArea.setText("Doktor tanımlamaları getirilirken hata: " + ex.getMessage());
            showAlert(Alert.AlertType.ERROR, "Veritabanı Hatası", "Doktor tanımlamaları getirilirken hata: " + ex.getMessage());
        }

        Button backButton = new Button("Geri");
        backButton.setOnAction(e -> hastaPaneli(primaryStage, hastaTcKimlik));
        layout.getChildren().add(backButton);

        Scene scene = new Scene(layout, 500, 400);
        primaryStage.setScene(scene);
        primaryStage.setTitle("Hastane Otomasyonu - Doktor Tanımlamaları");
    }

    // Hasta için Günlük Takip Giriş Ekranı
    private void gunlukTakipGirisi(Stage primaryStage, String hastaTcKimlik) {
        GridPane grid = new GridPane();
        grid.setAlignment(Pos.CENTER);
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(25));

        Text title = new Text("Günlük Takip Girişi");
        title.setFont(Font.font("Tahoma", FontWeight.NORMAL, 20));
        grid.add(title, 0, 0, 2, 1);

        Label tcNoLabel = new Label("TC Kimlik No:");
        grid.add(tcNoLabel, 0, 1);
        TextField tcNoField = new TextField(hastaTcKimlik);
        tcNoField.setEditable(false);
        grid.add(tcNoField, 1, 1);

        Label tarihLabel = new Label("Tarih (GG-AA-YYYY):");
        grid.add(tarihLabel, 0, 2);
        TextField tarihField = new TextField();
        tarihField.setPromptText("GG-AA-YYYY");

        grid.add(tarihField, 1, 2);

        Label egzersizLabel = new Label("Egzersiz Durumu:");
        grid.add(egzersizLabel, 0, 3);
        ComboBox<String> egzersizComboBox = new ComboBox<>();
        egzersizComboBox.getItems().addAll("yapıldı", "yapılmadı");
        egzersizComboBox.setValue("yapıldı");
        grid.add(egzersizComboBox, 1, 3);

        Label diyetLabel = new Label("Diyet Durumu:");
        grid.add(diyetLabel, 0, 4);
        ComboBox<String> diyetComboBox = new ComboBox<>();
        diyetComboBox.getItems().addAll("uygulandı", "uygulanmadı");
        diyetComboBox.setValue("uygulandı");
        grid.add(diyetComboBox, 1, 4);

        Label ekBelirtilerLabel = new Label("Ek Belirtiler (isteğe bağlı):");
        grid.add(ekBelirtilerLabel, 0, 5);
        TextArea ekBelirtilerArea = new TextArea();
        ekBelirtilerArea.setPromptText("Yeni belirtileri veya özel durumları buraya yazın.");
        ekBelirtilerArea.setPrefRowCount(3);
        grid.add(ekBelirtilerArea, 1, 5);

        Button kaydetButton = new Button("Kaydet");
        Button backButton = new Button("Geri");
        HBox hbButtons = new HBox(10);
        hbButtons.setAlignment(Pos.BOTTOM_RIGHT);
        hbButtons.getChildren().addAll(backButton, kaydetButton);
        grid.add(hbButtons, 1, 6);

        final Text messageText = new Text();
        grid.add(messageText, 1, 7);

        kaydetButton.setOnAction(e -> {
            String egzersizDurumu = egzersizComboBox.getValue();
            String diyetDurumu = diyetComboBox.getValue();
            String ekBelirtiler = ekBelirtilerArea.getText();

            LocalDate tarih;
            try {
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy");
                tarih = LocalDate.parse(tarihField.getText(), formatter);
            } catch (DateTimeParseException ex) {
                showAlert(Alert.AlertType.ERROR, "Tarih Hatası", "Lütfen geçerli bir tarih girin (GG-AA-YYYY).");
                return;
            }

            try {
                String sql = "INSERT INTO gunluk_takip (hasta_tc_no, tarih, egzersiz_durumu, diyet_durumu, ek_belirtiler) "
                        + "VALUES (?, ?, ?, ?, ?) "
                        + "ON DUPLICATE KEY UPDATE egzersiz_durumu = VALUES(egzersiz_durumu), "
                        + "diyet_durumu = VALUES(diyet_durumu), ek_belirtiler = VALUES(ek_belirtiler)";

                try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                    pstmt.setString(1, hastaTcKimlik);
                    pstmt.setObject(2, tarih);
                    pstmt.setString(3, egzersizDurumu);
                    pstmt.setString(4, diyetDurumu);
                    pstmt.setString(5, ekBelirtiler);

                    int rowsAffected = pstmt.executeUpdate();
                    if (rowsAffected > 0) {
                        messageText.setFill(Color.GREEN);
                        messageText.setText("Günlük takip bilgileri başarıyla kaydedildi/güncellendi.");
                        egzersizComboBox.setValue("yapıldı");
                        diyetComboBox.setValue("uygulandı");
                        ekBelirtilerArea.clear();
                        showAlert(Alert.AlertType.INFORMATION, "Başarılı", "Günlük takip kaydedildi.");
                    } else {
                        messageText.setFill(Color.RED);
                        messageText.setText("Günlük takip bilgileri kaydedilemedi.");
                    }
                }
            } catch (SQLException ex) {
                messageText.setFill(Color.RED);
                messageText.setText("Veritabanı hatası: " + ex.getMessage());
                showAlert(Alert.AlertType.ERROR, "Veritabanı Hatası", "Günlük takip kaydedilirken hata: " + ex.getMessage());
            }
        });

        backButton.setOnAction(e -> hastaPaneli(primaryStage, hastaTcKimlik));

        Scene scene = new Scene(grid, 550, 500);
        primaryStage.setScene(scene);
        primaryStage.setTitle("Hastane Otomasyonu - Günlük Takip");
    }
    // Kan Şekeri Giriş Ekranı (Hasta için)

    private void KanSekeriGiris(Stage primaryStage, String hastaTcKimlik) {
        GridPane grid = new GridPane();
        grid.setAlignment(Pos.CENTER);
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(25));

        Text title = new Text("Kan Şekeri Ölçümü Girişi");
        title.setFont(Font.font("Tahoma", FontWeight.NORMAL, 20));
        grid.add(title, 0, 0, 2, 1);

        Label tcNoLabel = new Label("TC Kimlik No:");
        grid.add(tcNoLabel, 0, 1);
        TextField tcNoField = new TextField(hastaTcKimlik); // Hasta TC'si otomatik doldurulur
        tcNoField.setEditable(false); // Düzenlenemez yapar
        grid.add(tcNoField, 1, 1);

        Label tarihLabel = new Label("Tarih (GG-AA-YYYY):");
        grid.add(tarihLabel, 0, 2);
        TextField tarihField = new TextField(LocalDate.now().format(DATE_FORMATTER)); // Bugünün tarihini varsayılan olarak ayarla
        tarihField.setPromptText("Örn: 26-10-2023");
        grid.add(tarihField, 1, 2);

        Label saatLabel = new Label("Saat (HH:MM:SS):");
        grid.add(saatLabel, 0, 3);
        TextField saatField = new TextField(LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))); // Şu anki saati varsayılan olarak ayarla
        saatField.setPromptText("Örn: 07:30:00");
        grid.add(saatField, 1, 3);

        Label degerLabel = new Label("Şeker Değeri (mg/dL):");
        grid.add(degerLabel, 0, 4);
        TextField degerField = new TextField();
        grid.add(degerField, 1, 4);

        Button kaydetButton = new Button("Kaydet");
        Button backButton = new Button("Geri");
        HBox hbButtons = new HBox(10);
        hbButtons.setAlignment(Pos.BOTTOM_RIGHT);
        hbButtons.getChildren().addAll(backButton, kaydetButton);
        grid.add(hbButtons, 1, 5);

        final Text messageText = new Text();
        grid.add(messageText, 1, 6);

        kaydetButton.setOnAction(e -> {
            // tcNoField'dan gelen değer zaten hastaTcKimlik olacak
            String olcumTarihiStr = tarihField.getText();
            String olcumSaatiStr = saatField.getText();
            String sekerDegeriStr = degerField.getText();

            if (olcumTarihiStr.isEmpty() || olcumSaatiStr.isEmpty() || sekerDegeriStr.isEmpty()) {
                messageText.setFill(Color.RED);
                messageText.setText("Tüm alanları doldurunuz!");
                return;
            }

            // Tarih formatı kontrolü ve parse etme (DD-MM-YYYY)
            LocalDate olcumTarihi;
            try {
                olcumTarihi = LocalDate.parse(olcumTarihiStr, DATE_FORMATTER);
            } catch (DateTimeParseException ex) {
                messageText.setFill(Color.RED);
                messageText.setText("Tarih formatı hatalı. Lütfen GG-AA-YYYY formatında girin.");
                return;
            }

            // Saat formatı kontrolü
            LocalTime saat = LocalTime.parse(olcumSaatiStr);
            String zamanDilimi = zamanDilimiBelirle(saat);

            if (zamanDilimi.equals("gecersiz")) {
                messageText.setFill(Color.ORANGE);
                messageText.setText("Zaman dışı ölçüm kaydedildi. Ancak ortalamaya dahil edilmeyecek.");
            } else {
                messageText.setFill(Color.GREEN);
                messageText.setText("Geçerli zaman diliminde ölçüm yapıldı.");
            }

            if (zamanDilimi.equals("gecersiz")) {
                messageText.setFill(Color.RED);
                messageText.setText("Saat geçersiz zaman diliminde. Lütfen doğru aralıkta saat girin.");

            }

// Aynı gün ve zaman diliminde kayıt varsa engelle
            String checkSql = "SELECT COUNT(*) FROM kan_sekeri WHERE hasta_tc_no = ? AND tarih = ? AND zaman_dilimi = ?";
            try (PreparedStatement checkStmt = connection.prepareStatement(checkSql)) {
                checkStmt.setString(1, hastaTcKimlik);
                checkStmt.setObject(2, olcumTarihi);
                checkStmt.setString(3, zamanDilimi);

                try (ResultSet rs = checkStmt.executeQuery()) {
                    if (rs.next() && rs.getInt(1) > 0) {
                        messageText.setFill(Color.RED);
                        messageText.setText("Bu zaman diliminde zaten ölçüm yapılmış.");
                        return;
                    }
                }
            } catch (SQLException sqlEx) {
                sqlEx.printStackTrace();
                // hata konsola yazılır (geliştirme için)
                messageText.setFill(Color.RED);
                messageText.setText("Veritabanı hatası oluştu.");
            }

            try {
                float sekerDegeri = Float.parseFloat(sekerDegeriStr);

                String insertSekerSql = "INSERT INTO kan_sekeri (hasta_tc_no, tarih, saat, seker_degeri, giris_turu, zaman_dilimi) VALUES (?, ?, ?, ?, 'hasta', ?)";

                try (PreparedStatement sekerPstmt = connection.prepareStatement(insertSekerSql)) {
                    sekerPstmt.setString(1, hastaTcKimlik);
                    sekerPstmt.setObject(2, olcumTarihi); // LocalDate objesini doğrudan kaydedin
                    sekerPstmt.setString(3, olcumSaatiStr);
                    sekerPstmt.setFloat(4, sekerDegeri);
                    sekerPstmt.setString(5, zamanDilimi);

                    int sekerInserted = sekerPstmt.executeUpdate();
                    if (sekerInserted > 0) {
                        messageText.setFill(Color.GREEN);
                        messageText.setText("Kan şekeri değeri başarıyla kaydedildi.");
                        // Formu temizle ve güncel varsayılan değerleri ayarla
                        tarihField.setText(LocalDate.now().format(DATE_FORMATTER));
                        saatField.setText(LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")));
                        degerField.clear();
                        showAlert(Alert.AlertType.INFORMATION, "Başarılı", "Kan şekeri değeri kaydedildi.");
                    } else {
                        messageText.setFill(Color.RED);
                        messageText.setText("Kan şekeri değeri kaydedilemedi.");
                    }
                }
            } catch (NumberFormatException ex) {
                messageText.setFill(Color.RED);
                messageText.setText("Geçersiz kan şekeri değeri girdiniz. Lütfen sayısal bir değer girin.");
            } catch (SQLException ex) {
                messageText.setFill(Color.RED);
                messageText.setText("Veritabanı hatası: " + ex.getMessage());
                showAlert(Alert.AlertType.ERROR, "Veritabanı Hatası", "Kan şekeri kaydedilirken hata: " + ex.getMessage());
            }
        });

        backButton.setOnAction(event -> hastaPaneli(primaryStage, hastaTcKimlik));

        Scene scene = new Scene(grid, 500, 450);
        primaryStage.setScene(scene);
        primaryStage.setTitle("Hastane Otomasyonu - Kan Şekeri Girişi");
    }

    // Kan Şekeri Geçmişi Görüntüleme Ekranı (Hasta)
    private void kanSekeriGecmisi(Stage primaryStage, String hastaTcKimlik) {
        VBox layout = new VBox(15);
        layout.setAlignment(Pos.TOP_CENTER);
        layout.setPadding(new Insets(20));

        Text title = new Text("Kan Şekeri Ölçüm Geçmişi");
        title.setFont(Font.font("Arial", FontWeight.BOLD, 20));
        layout.getChildren().add(title);

        TextArea historyArea = new TextArea();
        historyArea.setEditable(false);
        historyArea.setPrefRowCount(15);
        layout.getChildren().add(historyArea);

        try {
            historyArea.setText(KanSekeriGecmisi(hastaTcKimlik));
        } catch (SQLException ex) {
            historyArea.setText("Geçmiş getirilirken hata: " + ex.getMessage());
            showAlert(Alert.AlertType.ERROR, "Veritabanı Hatası", "Kan şekeri geçmişi getirilirken hata: " + ex.getMessage());
        }

        Button backButton = new Button("Geri");
        backButton.setOnAction(e -> hastaPaneli(primaryStage, hastaTcKimlik));
        layout.getChildren().add(backButton);

        Scene scene = new Scene(layout, 600, 600);
        primaryStage.setScene(scene);
        primaryStage.setTitle("Hastane Otomasyonu - Kan Şekeri Geçmişi");
    }

    public static boolean SaatAraligindaMi(String saatStr) {
        try {
            LocalTime saat = LocalTime.parse(saatStr);
            LocalTime sabahBasla = LocalTime.of(7, 0);
            LocalTime sabahBitis = LocalTime.of(8, 0);
            LocalTime ogleBasla = LocalTime.of(12, 0);
            LocalTime ogleBitis = LocalTime.of(13, 0);
            LocalTime ikindiBasla = LocalTime.of(15, 0);
            LocalTime ikindiBitis = LocalTime.of(16, 0);
            LocalTime aksamBasla = LocalTime.of(18, 0);
            LocalTime aksamBitis = LocalTime.of(19, 0);
            LocalTime geceBasla = LocalTime.of(22, 0);
            LocalTime geceBitis = LocalTime.of(23, 0);

            return (saat.compareTo(sabahBasla) >= 0 && saat.compareTo(sabahBitis) <= 0)
                    || (saat.compareTo(ogleBasla) >= 0 && saat.compareTo(ogleBitis) <= 0)
                    || (saat.compareTo(ikindiBasla) >= 0 && saat.compareTo(ikindiBitis) <= 0)
                    || (saat.compareTo(aksamBasla) >= 0 && saat.compareTo(aksamBitis) <= 0)
                    || (saat.compareTo(geceBasla) >= 0 && saat.compareTo(geceBitis) <= 0);

        } catch (DateTimeParseException e) { // Saatin parse edilememesi durumunu yakala
            System.out.println("Saat formatı hatalı! Lütfen HH:MM:SS formatında giriniz.");
            return false;
        } catch (Exception e) { // Diğer olası hatalar
            System.out.println("Saat kontrolünde beklenmeyen bir hata oluştu: " + e.getMessage());
            return false;
        }
    }

    private String zamanDilimiBelirle(LocalTime saat) {
        if (saat.isAfter(LocalTime.of(6, 59)) && saat.isBefore(LocalTime.of(8, 1))) {
            return "sabah";
        }
        if (saat.isAfter(LocalTime.of(11, 59)) && saat.isBefore(LocalTime.of(13, 1))) {
            return "ogle";
        }
        if (saat.isAfter(LocalTime.of(14, 59)) && saat.isBefore(LocalTime.of(16, 1))) {
            return "ikindi";
        }
        if (saat.isAfter(LocalTime.of(17, 59)) && saat.isBefore(LocalTime.of(19, 1))) {
            return "aksam";
        }
        if (saat.isAfter(LocalTime.of(21, 59)) && saat.isBefore(LocalTime.of(23, 1))) {
            return "gece";
        }
        return "gecersiz";
    }

    // Uyarı mesajları göstermek için yardımcı metod
    private void showAlert(Alert.AlertType alertType, String title, String message) {
        Alert alert = new Alert(alertType);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    public static void main(String[] args) {
        hashleVeGuncelleAdminSifreleri(); // Admin şifrelerini hashle
        hashleVeGuncelleHastaSifreleri(); // Hasta şifrelerini hashle
        Application.launch(ProLab.class, args); // ardından JavaFX başlat

    }

    public static void hashleVeGuncelleAdminSifreleri() {
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
            String selectSQL = "SELECT tc_no, sifre FROM admin";
            String updateSQL = "UPDATE admin SET sifre = ? WHERE tc_no = ?";

            try (PreparedStatement selectStmt = conn.prepareStatement(selectSQL)) {
                ResultSet rs = selectStmt.executeQuery();

                while (rs.next()) {
                    String tcNo = rs.getString("tc_no");
                    String sifre = rs.getString("sifre");

                    // Şifre zaten hashlenmişse ($2a$ ile başlıyorsa) atla
                    if (sifre != null && sifre.startsWith("$2a$")) {
                        continue;
                    }

                    String hashed = BCrypt.hashpw(sifre, BCrypt.gensalt());

                    PreparedStatement updateStmt = conn.prepareStatement(updateSQL);
                    updateStmt.setString(1, hashed);
                    updateStmt.setString(2, tcNo);
                    updateStmt.executeUpdate();

                    System.out.println("TC: " + tcNo + " için şifre hashlenip güncellendi:");
                    System.out.println("Yeni hash: " + hashed);
                }

                rs.close();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static void hashleVeGuncelleHastaSifreleri() {
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
            String selectSQL = "SELECT tc_no, sifre FROM kullanicilar";
            String updateSQL = "UPDATE kullanicilar SET sifre = ? WHERE tc_no = ?";

            try (PreparedStatement selectStmt = conn.prepareStatement(selectSQL)) {
                ResultSet rs = selectStmt.executeQuery();

                while (rs.next()) {
                    String tcNo = rs.getString("tc_no");
                    String sifre = rs.getString("sifre");

                    // Şifre zaten hashlenmişse atla (BCrypt hash genelde $2a$ ile başlar)
                    if (sifre != null && sifre.startsWith("$2a$")) {
                        continue;
                    }

                    String hashed = BCrypt.hashpw(sifre, BCrypt.gensalt());

                    try (PreparedStatement updateStmt = conn.prepareStatement(updateSQL)) {
                        updateStmt.setString(1, hashed);
                        updateStmt.setString(2, tcNo);
                        updateStmt.executeUpdate();
                        System.out.println("Hasta TC: " + tcNo + " için şifre hashlenip güncellendi.");
                    }
                }

                rs.close();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void HastaGrafikselTakip(Stage primaryStage, String hastaTc) {
        VBox layout = new VBox(15);
        layout.setPadding(new Insets(20));
        layout.setAlignment(Pos.TOP_CENTER);

        Text title = new Text("Kan Şekeri - Egzersiz - Diyet İlişkisi");
        title.setFont(Font.font("Arial", FontWeight.BOLD, 20));
        layout.getChildren().add(title);

        CategoryAxis xAxis = new CategoryAxis();
        xAxis.setLabel("Tarih");
        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel("Kan Şekeri (mg/dL)");

        LineChart<String, Number> lineChart = new LineChart<>(xAxis, yAxis);
        lineChart.setTitle("Kan Şekeri Takibi");

        XYChart.Series<String, Number> sekerSerisi = new XYChart.Series<>();
        sekerSerisi.setName("Kan Şekeri");

        try {
            String sql = "SELECT ks.tarih, ks.seker_degeri, ks.giris_turu, gt.egzersiz_durumu, gt.diyet_durumu "
                    + "FROM kan_sekeri ks "
                    + "LEFT JOIN gunluk_takip gt ON ks.hasta_tc_no = gt.hasta_tc_no AND ks.tarih = gt.tarih "
                    + "WHERE ks.hasta_tc_no = ? ORDER BY ks.tarih ASC";
            PreparedStatement pstmt = connection.prepareStatement(sql);
            pstmt.setString(1, hastaTc);
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                String tarih = rs.getDate("tarih").toLocalDate().format(DateTimeFormatter.ofPattern("dd-MM-yyyy"));
                float seker = rs.getFloat("seker_degeri");
                String egzersiz = rs.getString("egzersiz_durumu");
                String diyet = rs.getString("diyet_durumu");
                String girisTuru = rs.getString("giris_turu");

                XYChart.Data<String, Number> data = new XYChart.Data<>(tarih, seker);
                sekerSerisi.getData().add(data);

                // Tooltip
                String tooltipText = "Egzersiz: " + (egzersiz != null ? egzersiz : "Bilinmiyor")
                        + "\nDiyet: " + (diyet != null ? diyet : "Bilinmiyor")
                        + "\nGiriş Türü: " + girisTuru;

                data.nodeProperty().addListener((obs, oldNode, newNode) -> {
                    if (newNode != null) {
                        Tooltip.install(newNode, new Tooltip(tooltipText));

                        // Giriş türüne göre renk ata
                        if ("admin".equals(girisTuru)) {
                            newNode.setStyle("-fx-background-color: red, white;");
                        } else {
                            newNode.setStyle("-fx-background-color: green, white;");
                        }
                    }
                });

                data.nodeProperty().addListener((obs, oldNode, newNode) -> {
                    if (newNode != null) {
                        Tooltip.install(newNode, new Tooltip(tooltipText));

                        // 🔴 Kimin girdiğine göre stil ata
                        if ("admin".equals(girisTuru)) {
                            newNode.setStyle("-fx-background-color: red, white;");
                        } else {
                            newNode.setStyle("-fx-background-color: green, white;");
                        }
                    }
                });
            }

            rs.close();
            pstmt.close();
        } catch (SQLException e) {
            showAlert(Alert.AlertType.ERROR, "Veri Hatası", "Veri çekilirken hata: " + e.getMessage());
            e.printStackTrace();
        }

        lineChart.getData().add(sekerSerisi);
        layout.getChildren().add(lineChart);

        Button backButton = new Button("Geri");
        backButton.setOnAction(e -> adminPaneliEkrani(primaryStage, girisYapanAdminTcNo));
        layout.getChildren().add(backButton);

        Scene scene = new Scene(layout, 800, 600);
        primaryStage.setScene(scene);
        primaryStage.setTitle("Grafiksel Takip Ekranı");
    }

    private void HastaFiltrelemeEkrani(Stage primaryStage) {
        VBox layout = new VBox(15);
        layout.setPadding(new Insets(20));
        layout.setAlignment(Pos.TOP_CENTER);

        Text title = new Text("Hasta Listeleme ve Filtreleme");
        title.setFont(Font.font("Arial", FontWeight.BOLD, 20));
        layout.getChildren().add(title);

        // Filtre alanları
        HBox filterBox = new HBox(10);
        filterBox.setAlignment(Pos.CENTER);

        TextField minSekerField = new TextField();
        minSekerField.setPromptText("Min Kan Şekeri");

        TextField maxSekerField = new TextField();
        maxSekerField.setPromptText("Max Kan Şekeri");

        TextField belirtiField = new TextField();
        belirtiField.setPromptText("Belirti (isteğe bağlı)");

        Button filterButton = new Button("Filtrele");
        filterBox.getChildren().addAll(minSekerField, maxSekerField, belirtiField, filterButton);
        layout.getChildren().add(filterBox);

        TextArea resultArea = new TextArea();
        resultArea.setEditable(false);
        resultArea.setPrefRowCount(20);
        layout.getChildren().add(resultArea);

        filterButton.setOnAction(e -> {
            String minStr = minSekerField.getText().trim();
            String maxStr = maxSekerField.getText().trim();
            String belirti = belirtiField.getText().trim();

            try {
                float min = minStr.isEmpty() ? 0 : Float.parseFloat(minStr);
                float max = maxStr.isEmpty() ? 1000 : Float.parseFloat(maxStr);

                List<String> hastaTCList = new ArrayList<>();
                Map<String, String> hastaBilgileriMap = new HashMap<>();

                String sql = "SELECT k.tc_no, k.ad, k.soyad, AVG(s.seker_degeri) as ortalama "
                        + "FROM kullanicilar k "
                        + "JOIN kan_sekeri s ON k.tc_no = s.hasta_tc_no "
                        + "WHERE s.seker_degeri BETWEEN ? AND ? "
                        + "GROUP BY k.tc_no";

                PreparedStatement pstmt = connection.prepareStatement(sql);
                pstmt.setFloat(1, min);
                pstmt.setFloat(2, max);

                ResultSet rs = pstmt.executeQuery();
                while (rs.next()) {
                    String tc = rs.getString("tc_no");
                    float ort = rs.getFloat("ortalama");
                    hastaTCList.add(tc);
                    hastaBilgileriMap.put(tc, "TC: " + tc
                            + " | Ad: " + rs.getString("ad")
                            + " " + rs.getString("soyad")
                            + " | Ortalama Şeker: " + String.format("%.1f", ort) + "\n");
                }
                rs.close();
                pstmt.close();

                // Belirti filtresi varsa
                if (!belirti.isEmpty()) {
                    List<String> gecerliTCList = new ArrayList<>();
                    for (String tc : hastaTCList) {
                        String belirtiSQL = "SELECT 1 FROM belirtiler WHERE hasta_tc_no = ? AND belirti_adi LIKE ?";
                        try (PreparedStatement ps = connection.prepareStatement(belirtiSQL)) {
                            ps.setString(1, tc);
                            ps.setString(2, "%" + belirti + "%");
                            ResultSet rsBelirti = ps.executeQuery();
                            if (rsBelirti.next()) {
                                gecerliTCList.add(tc);
                            }
                        }
                    }
                    hastaTCList = gecerliTCList; // Sadece belirtili olanları al
                }

                // Sonucu yaz
                if (hastaTCList.isEmpty()) {
                    resultArea.setText("Filtreye uyan hasta bulunamadı.");
                } else {
                    StringBuilder sb = new StringBuilder();
                    for (String tc : hastaTCList) {
                        sb.append(hastaBilgileriMap.get(tc));
                    }
                    resultArea.setText(sb.toString());
                }

            } catch (Exception ex) {
                showAlert(Alert.AlertType.ERROR, "Filtreleme Hatası", "Hata: " + ex.getMessage());
            }
        });

        Button backButton = new Button("Geri");
        backButton.setOnAction(e -> adminPaneliEkrani(primaryStage, girisYapanAdminTcNo));
        layout.getChildren().add(backButton);

        Scene scene = new Scene(layout, 700, 600);
        primaryStage.setScene(scene);
        primaryStage.setTitle("Hastane Otomasyonu - Hasta Listeleme / Filtreleme");
    }

    private void HastaUygulamaOranGrafik(Stage primaryStage, String hastaTcNo) {
        VBox layout = new VBox(15);
        layout.setAlignment(Pos.TOP_CENTER);
        layout.setPadding(new Insets(20));

        Text title = new Text("Egzersiz ve Diyet Uygulama Oranları");
        title.setFont(Font.font("Arial", FontWeight.BOLD, 20));
        layout.getChildren().add(title);

        CategoryAxis xAxis = new CategoryAxis();
        xAxis.setLabel("Uygulama Türü");

        NumberAxis yAxis = new NumberAxis(0, 100, 10);
        yAxis.setLabel("Uygulanma Oranı (%)");

        BarChart<String, Number> barChart = new BarChart<>(xAxis, yAxis);
        barChart.setTitle("Hasta Egzersiz & Diyet Uyum Yüzdeleri");

        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName("Uygulama Oranları");

        try {
            String sql = "SELECT egzersiz_durumu, diyet_durumu FROM gunluk_takip WHERE hasta_tc_no = ?";
            PreparedStatement pstmt = connection.prepareStatement(sql);
            pstmt.setString(1, hastaTcNo);
            ResultSet rs = pstmt.executeQuery();

            int toplamEgzersizKaydi = 0;
            int egzersizYapildi = 0;
            int toplamDiyetKaydi = 0;
            int diyetUygulandi = 0;

            while (rs.next()) {
                String egzersiz = rs.getString("egzersiz_durumu");
                String diyet = rs.getString("diyet_durumu");

                if (egzersiz != null) {
                    toplamEgzersizKaydi++;
                    if (egzersiz.equalsIgnoreCase("yapıldı")) {
                        egzersizYapildi++;
                    }
                }

                if (diyet != null) {
                    toplamDiyetKaydi++;
                    if (diyet.equalsIgnoreCase("uygulandı")) {
                        diyetUygulandi++;
                    }
                }
            }

            rs.close();
            pstmt.close();

            double egzersizOran = toplamEgzersizKaydi > 0 ? (egzersizYapildi * 100.0 / toplamEgzersizKaydi) : 0;
            double diyetOran = toplamDiyetKaydi > 0 ? (diyetUygulandi * 100.0 / toplamDiyetKaydi) : 0;

            series.getData().add(new XYChart.Data<>("Egzersiz", egzersizOran));
            series.getData().add(new XYChart.Data<>("Diyet", diyetOran));

            barChart.getData().add(series);
            layout.getChildren().add(barChart);
            // Günlük durumları listele
            TextArea detayArea = new TextArea();
            detayArea.setEditable(false);
            detayArea.setPrefRowCount(10);
            detayArea.setPrefWidth(500);
            layout.getChildren().add(new Text("🔍 Günlük Egzersiz/Diyet Durumu:"));
            layout.getChildren().add(detayArea);

            StringBuilder sb = new StringBuilder();
            sb.append("Tarih       | Egzersiz     | Diyet        | Belirtiler\n");
            sb.append("--------------------------------------------------------\n");

            String detaySql = "SELECT tarih, egzersiz_durumu, diyet_durumu, ek_belirtiler "
                    + "FROM gunluk_takip WHERE hasta_tc_no = ? ORDER BY tarih DESC LIMIT 10";

            try (PreparedStatement detayStmt = connection.prepareStatement(detaySql)) {
                detayStmt.setString(1, hastaTcNo);
                ResultSet rsDetay = detayStmt.executeQuery();
                while (rsDetay.next()) {
                    LocalDate tarih = rsDetay.getDate("tarih").toLocalDate();
                    String egzersiz = rsDetay.getString("egzersiz_durumu");
                    String diyet = rsDetay.getString("diyet_durumu");
                    String belirtiler = rsDetay.getString("ek_belirtiler");

                    sb.append(String.format("%s | %-12s | %-12s | %s\n",
                            tarih.format(DATE_FORMATTER),
                            egzersiz != null ? egzersiz : "-",
                            diyet != null ? diyet : "-",
                            (belirtiler != null && !belirtiler.isBlank()) ? belirtiler : "-"));
                }
            }
            detayArea.setText(sb.toString());

        } catch (SQLException e) {
            showAlert(Alert.AlertType.ERROR, "Veri Hatası", "Veri alınırken hata oluştu: " + e.getMessage());
        }

        Button backButton = new Button("Geri");
        backButton.setOnAction(e -> adminPaneliEkrani(primaryStage, girisYapanAdminTcNo));
        layout.getChildren().add(backButton);

        Scene scene = new Scene(layout, 600, 500);
        primaryStage.setScene(scene);
        primaryStage.setTitle("Egzersiz ve Diyet Uyum Oranı");
    }

    public void sekerOrtalamasiFiltreliGoster(Connection connection) {
        // 1. Minimum kan şekeri değeri al
        TextInputDialog minDialog = new TextInputDialog();
        minDialog.setTitle("Alt Kan Şekeri Sınırı");
        minDialog.setHeaderText("Alt sınırı girin:");
        minDialog.setContentText("Min:");

        Optional<String> minInput = minDialog.showAndWait();

        // 2. Maksimum kan şekeri değeri al
        TextInputDialog maxDialog = new TextInputDialog();
        maxDialog.setTitle("Üst Kan Şekeri Sınırı");
        maxDialog.setHeaderText("Üst sınırı girin:");
        maxDialog.setContentText("Max:");

        Optional<String> maxInput = maxDialog.showAndWait();

        // 3. Belirti gir
        TextInputDialog belirtiDialog = new TextInputDialog();
        belirtiDialog.setTitle("Belirti Filtrelemesi");
        belirtiDialog.setHeaderText("Belirtileri girin (virgülle ayırın):");
        belirtiDialog.setContentText("Belirti(ler):");

        Optional<String> belirtiInput = belirtiDialog.showAndWait();

        if (minInput.isPresent() && maxInput.isPresent() && belirtiInput.isPresent()) {
            try {
                double min = Double.parseDouble(minInput.get());
                double max = Double.parseDouble(maxInput.get());
                String[] belirtiler = belirtiInput.get().split(",");

                // SQL Sorgusu
                String sql = "SELECT k.tc_no, k.ad, k.soyad, AVG(ks.seker_degeri) AS ortalama_seker_degeri, "
                        + "GROUP_CONCAT(DISTINCT b.belirti_adi) AS belirtiler "
                        + "FROM kullanicilar k "
                        + "JOIN kan_sekeri ks ON k.tc_no = ks.hasta_tc_no "
                        + "LEFT JOIN belirtiler b ON k.tc_no = b.hasta_tc_no "
                        + "WHERE ks.seker_degeri BETWEEN ? AND ? "
                        + "AND b.belirti_adi IN (" + String.join(",", Arrays.stream(belirtiler).map(s -> "?").toArray(String[]::new)) + ") "
                        + "GROUP BY k.tc_no, k.ad, k.soyad "
                        + "ORDER BY ortalama_seker_degeri DESC";

                PreparedStatement ps = connection.prepareStatement(sql);
                ps.setDouble(1, min);
                ps.setDouble(2, max);

                for (int i = 0; i < belirtiler.length; i++) {
                    ps.setString(3 + i, belirtiler[i].trim());
                }

                ResultSet rs = ps.executeQuery();

                while (rs.next()) {
                    String tcNo = rs.getString("tc_no");
                    String ad = rs.getString("ad");
                    String soyad = rs.getString("soyad");
                    double ort = rs.getDouble("ortalama_seker_degeri");
                    String bulunanBelirtiler = rs.getString("belirtiler");

                    System.out.println(tcNo + " - " + ad + " " + soyad
                            + " | Ortalama Şeker: " + ort
                            + " | Belirtiler: " + bulunanBelirtiler);
                }

            } catch (NumberFormatException e) {
                System.out.println("Hatalı sayı girdiniz.");
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    private void gunlukSekerIstatistik(Stage primaryStage, String hastaTcNo) {
        VBox layout = new VBox(15);
        layout.setPadding(new Insets(20));
        layout.setAlignment(Pos.TOP_CENTER);

        Text title = new Text("Günlük Kan Şekeri Değerleri ve Ortalama");
        title.setFont(Font.font("Arial", FontWeight.BOLD, 20));
        layout.getChildren().add(title);

        TextArea dataArea = new TextArea();
        dataArea.setEditable(false);
        dataArea.setPrefRowCount(20);
        layout.getChildren().add(dataArea);

        StringBuilder sb = new StringBuilder();
        try {
            String sql = """
            SELECT tarih, zaman_dilimi, AVG(seker_degeri) AS ortalama_seker
            FROM kan_sekeri
            WHERE hasta_tc_no = ? 
              AND giris_turu = 'hasta'
              AND zaman_dilimi IN ('sabah', 'ogle', 'ikindi', 'aksam', 'gece')
            GROUP BY tarih, zaman_dilimi
            ORDER BY tarih DESC, zaman_dilimi;
        """;

            PreparedStatement pstmt = connection.prepareStatement(sql);
            pstmt.setString(1, hastaTcNo);
            ResultSet rs = pstmt.executeQuery();

            sb.append("Tarih       | Zaman Dilimi | Ortalama Şeker (mg/dL)\n");
            sb.append("---------------------------------------------------\n");

            while (rs.next()) {
                LocalDate tarih = rs.getDate("tarih").toLocalDate();
                String zamanDilimi = rs.getString("zaman_dilimi");
                double ort = rs.getDouble("ortalama_seker");

                sb.append(String.format("%s | %-12s | %.1f\n",
                        tarih.format(DATE_FORMATTER),
                        zamanDilimi,
                        ort));
            }

            // Yeni Günlük Ortalama Hesaplama
            String dailyAverageSql = """
            SELECT tarih, AVG(seker_degeri) AS daily_average
            FROM kan_sekeri
            WHERE hasta_tc_no = ? 
              AND giris_turu = 'hasta'
              AND zaman_dilimi IN ('sabah', 'ogle', 'ikindi', 'aksam', 'gece')
            GROUP BY tarih
            ORDER BY tarih DESC;
        """;

            try (PreparedStatement dailyAvgPstmt = connection.prepareStatement(dailyAverageSql)) {
                dailyAvgPstmt.setString(1, hastaTcNo);
                ResultSet dailyAvgRs = dailyAvgPstmt.executeQuery();

                sb.append("\n🔸 Günlük Ortalama Kan Şekeri:\n");

                while (dailyAvgRs.next()) {
                    LocalDate tarih = dailyAvgRs.getDate("tarih").toLocalDate();
                    double dailyAverage = dailyAvgRs.getDouble("daily_average");

                    sb.append(String.format("%s | %.1f mg/dL\n", tarih.format(DATE_FORMATTER), dailyAverage));
                }
            }

            // Genel Ortalama Hesaplama
            String genelOrtalamaSql = """
            SELECT AVG(seker_degeri) AS genel_ortalama
            FROM kan_sekeri
            WHERE hasta_tc_no = ? 
              AND giris_turu = 'hasta'
              AND zaman_dilimi IN ('sabah', 'ogle', 'ikindi', 'aksam', 'gece');
        """;

            try (PreparedStatement genelPstmt = connection.prepareStatement(genelOrtalamaSql)) {
                genelPstmt.setString(1, hastaTcNo);
                ResultSet genelRs = genelPstmt.executeQuery();

                if (genelRs.next()) {
                    double genelOrtalama = genelRs.getDouble("genel_ortalama");
                    sb.append("\n🔸 Genel Ortalama Kan Şekeri: ").append(String.format("%.1f mg/dL", genelOrtalama));
                }
            }

            rs.close();
            pstmt.close();
        } catch (SQLException e) {
            showAlert(Alert.AlertType.ERROR, "Veri Hatası", "Veri çekilirken hata oluştu: " + e.getMessage());
            e.printStackTrace();
        }

        dataArea.setText(sb.toString());

        Button backButton = new Button("Geri");
        backButton.setOnAction(e -> hastaPaneli(primaryStage, hastaTcNo));
        layout.getChildren().add(backButton);

        Scene scene = new Scene(layout, 600, 500);
        primaryStage.setScene(scene);
        primaryStage.setTitle("Günlük Şeker ve Ortalama");
    }

    private void HastaUygulamaOranGrafikHasta(Stage primaryStage, String hastaTcNo) {
        VBox layout = new VBox(15);
        layout.setAlignment(Pos.TOP_CENTER);
        layout.setPadding(new Insets(20));

        Text title = new Text("Egzersiz ve Diyet Uygulama Oranlarınız");
        title.setFont(Font.font("Arial", FontWeight.BOLD, 20));
        layout.getChildren().add(title);

        CategoryAxis xAxis = new CategoryAxis();
        xAxis.setLabel("Uygulama Türü");

        NumberAxis yAxis = new NumberAxis(0, 100, 10);
        yAxis.setLabel("Uygulanma Oranı (%)");

        BarChart<String, Number> barChart = new BarChart<>(xAxis, yAxis);
        barChart.setTitle("Günlük Takip Uyum Yüzdeleri");

        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName("Uyum Oranlarınız");

        try {
            String sql = "SELECT egzersiz_durumu, diyet_durumu FROM gunluk_takip WHERE hasta_tc_no = ?";
            PreparedStatement pstmt = connection.prepareStatement(sql);
            pstmt.setString(1, hastaTcNo);
            ResultSet rs = pstmt.executeQuery();

            int toplamEgzersizKaydi = 0;
            int egzersizYapildi = 0;
            int toplamDiyetKaydi = 0;
            int diyetUygulandi = 0;

            while (rs.next()) {
                String egzersiz = rs.getString("egzersiz_durumu");
                String diyet = rs.getString("diyet_durumu");

                // Egzersiz kayıtları
                if (egzersiz != null && !egzersiz.trim().isEmpty()) {
                    toplamEgzersizKaydi++;
                    if (egzersiz.trim().equalsIgnoreCase("yapıldı")) {
                        egzersizYapildi++;
                    }
                }

                // Diyet kayıtları
                if (diyet != null && !diyet.trim().isEmpty()) {
                    toplamDiyetKaydi++;
                    if (diyet.trim().equalsIgnoreCase("uygulandı")) {
                        diyetUygulandi++;
                    }
                }
            }

            rs.close();
            pstmt.close();

            double egzersizOran = toplamEgzersizKaydi > 0 ? (egzersizYapildi * 100.0 / toplamEgzersizKaydi) : 0;
            double diyetOran = toplamDiyetKaydi > 0 ? (diyetUygulandi * 100.0 / toplamDiyetKaydi) : 0;

            series.getData().add(new XYChart.Data<>("Egzersiz", egzersizOran));
            series.getData().add(new XYChart.Data<>("Diyet", diyetOran));

            barChart.getData().add(series);
            layout.getChildren().add(barChart);

        } catch (SQLException e) {
            showAlert(Alert.AlertType.ERROR, "Veri Hatası", "Veri alınırken hata oluştu: " + e.getMessage());
        }

        Button backButton = new Button("Geri");
        backButton.setOnAction(e -> hastaPaneli(primaryStage, hastaTcNo));
        layout.getChildren().add(backButton);

        Scene scene = new Scene(layout, 600, 500);
        primaryStage.setScene(scene);
        primaryStage.setTitle("Egzersiz ve Diyet Uyum Oranı");
    }

    private void HastaGunlukSekeriGrafik(Stage primaryStage, String hastaTcNo) {
        VBox layout = new VBox(15);
        layout.setPadding(new Insets(20));
        layout.setAlignment(Pos.TOP_CENTER);

        Text title = new Text("Günlük Kan Şekeri Ortalamaları (Grafiksel)");
        title.setFont(Font.font("Arial", FontWeight.BOLD, 20));
        layout.getChildren().add(title);

        CategoryAxis xAxis = new CategoryAxis();
        xAxis.setLabel("Tarih");

        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel("Ortalama Kan Şekeri (mg/dL)");

        LineChart<String, Number> lineChart = new LineChart<>(xAxis, yAxis);
        lineChart.setTitle("Günlük Kan Şekeri Değerleri");

        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName("Ortalama Şeker");

        try {
            String sql = "SELECT tarih, AVG(seker_degeri) AS ortalama_seker "
                    + "FROM kan_sekeri WHERE hasta_tc_no = ? AND giris_turu = 'hasta' AND zaman_dilimi != 'gecersiz' GROUP BY tarih ORDER BY tarih ASC";

            PreparedStatement pstmt = connection.prepareStatement(sql);
            pstmt.setString(1, hastaTcNo);
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                LocalDate tarih = rs.getDate("tarih").toLocalDate();
                double ort = rs.getDouble("ortalama_seker");

                String formattedDate = tarih.format(DATE_FORMATTER);
                XYChart.Data<String, Number> data = new XYChart.Data<>(formattedDate, ort);
                series.getData().add(data);
            }

            rs.close();
            pstmt.close();

            lineChart.getData().add(series);
            layout.getChildren().add(lineChart);

        } catch (SQLException e) {
            showAlert(Alert.AlertType.ERROR, "Veri Hatası", "Kan şekeri grafiği verileri alınamadı: " + e.getMessage());
        }

        Button backButton = new Button("Geri");
        backButton.setOnAction(e -> hastaPaneli(primaryStage, hastaTcNo));
        layout.getChildren().add(backButton);

        Scene scene = new Scene(layout, 700, 500);
        primaryStage.setScene(scene);
        primaryStage.setTitle("Hasta - Günlük Kan Şekeri Grafiği");
    }

    private void InsulinKayitlari(Stage primaryStage, String hastaTcNo) {
        VBox layout = new VBox(15);
        layout.setPadding(new Insets(20));
        layout.setAlignment(Pos.TOP_CENTER);

        Text title = new Text("İnsülin Kayıtlarını Tarihe Göre Listele");
        title.setFont(Font.font("Arial", FontWeight.BOLD, 20));

        DatePicker startPicker = new DatePicker();
        DatePicker endPicker = new DatePicker();
        startPicker.setPromptText("Başlangıç Tarihi");
        endPicker.setPromptText("Bitiş Tarihi");

        Button listeleButton = new Button("Listele");
        TextArea resultArea = new TextArea();
        resultArea.setPrefRowCount(15);
        resultArea.setEditable(false);

        Button backButton = new Button("Geri");
        backButton.setOnAction(e -> hastaPaneli(primaryStage, hastaTcNo));

        listeleButton.setOnAction(e -> {
            LocalDate start = startPicker.getValue();
            LocalDate end = endPicker.getValue();

            if (start != null && end != null) {
                try {
                    String sql = """
                    SELECT ks.tarih, 
                           AVG(ks.seker_degeri) AS ortalama_seker, 
                           ik.uygulanan_doz
                    FROM kan_sekeri ks
                    LEFT JOIN insulin_kayit ik 
                      ON ks.hasta_tc_no = ik.hasta_tc_no AND ks.tarih = ik.tarih
                    WHERE ks.hasta_tc_no = ? AND ks.tarih BETWEEN ? AND ?
                    AND giris_turu = 'hasta'
                    AND giris_turu != 'admin'
                    AND zaman_dilimi != 'gecersiz'
                    GROUP BY ks.tarih, ik.uygulanan_doz
                    ORDER BY ks.tarih ASC
                """;

                    PreparedStatement pstmt = connection.prepareStatement(sql);
                    pstmt.setString(1, hastaTcNo);
                    pstmt.setDate(2, java.sql.Date.valueOf(start));
                    pstmt.setDate(3, java.sql.Date.valueOf(end));

                    ResultSet rs = pstmt.executeQuery();

                    StringBuilder sb = new StringBuilder();
                    sb.append("TARİH       | Ortalama Şeker | Önerilen İnsülin | Uygulanan\n");
                    sb.append("--------------------------------------------------------------\n");

                    while (rs.next()) {
                        LocalDate tarih = rs.getDate("tarih").toLocalDate();
                        double ort = rs.getDouble("ortalama_seker");
                        String uygulanan = rs.getString("uygulanan_doz");

                        String onerilen;
                        if (ort < 70) {
                            onerilen = "Yok";
                        } else if (ort <= 110) {
                            onerilen = "Yok";
                        } else if (ort <= 150) {
                            onerilen = "1 ml";
                        } else if (ort <= 200) {
                            onerilen = "2 ml";
                        } else {
                            onerilen = "3 ml";
                        }

                        sb.append(String.format("%s | %.1f mg/dL | %-17s  %s\n",
                                tarih.format(DATE_FORMATTER), ort, onerilen, (uygulanan != null ? uygulanan : "")));
                    }

                    resultArea.setText(sb.toString());

                } catch (SQLException ex) {
                    resultArea.setText("Veri çekilirken hata: " + ex.getMessage());
                    ex.printStackTrace();
                }
            } else {
                resultArea.setText("Lütfen geçerli tarih aralığı seçin.");
            }
        });

        layout.getChildren().addAll(title, startPicker, endPicker, listeleButton, resultArea, backButton);

        Scene scene = new Scene(layout, 600, 500);
        primaryStage.setScene(scene);
        primaryStage.setTitle("İnsülin Kayıtları");
    }

    public List<String> gunlukUyarilariGetir(Connection conn, String hastaTcNo) throws SQLException {
        List<String> uyarilar = new ArrayList<>();
        String sql = """
        SELECT tarih, COUNT(*) AS olcum_sayisi, MIN(seker_degeri) AS min_deger, MAX(seker_degeri) AS max_deger, AVG(seker_degeri) AS ort_deger
        FROM kan_sekeri
        WHERE hasta_tc_no = ?
        GROUP BY tarih
        ORDER BY tarih DESC
        LIMIT 7;
    """;

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, hastaTcNo);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    LocalDate tarih = rs.getDate("tarih").toLocalDate();
                    int sayi = rs.getInt("olcum_sayisi");
                    float min = rs.getFloat("min_deger");
                    float max = rs.getFloat("max_deger");
                    float ort = rs.getFloat("ort_deger");

                    StringBuilder mesaj = new StringBuilder();
                    mesaj.append(tarih.format(DateTimeFormatter.ofPattern("dd-MM-yyyy"))).append(" → ");

                    if (sayi == 0) {
                        mesaj.append("Ölçüm Eksik Uyarısı: Hasta gün boyunca kan şekeri ölçümü yapmamıştır. Acil takip önerilir.");
                    } else if (sayi < 3) {
                        mesaj.append("Ölçüm Yetersiz Uyarısı: Günlük kan şekeri ölçüm sayısı yetersiz (").append(sayi).append(" giriş). Durum izlenmelidir.");
                    }

                    // Ortalama şeker seviyesine göre uyarılar
                    if (ort < 70) {
                        mesaj.append(" | Acil Uyarı: Hastanın kan şekeri seviyesi 70 mg/dL'nin altına düştü. Hipoglisemi riski! Hızlı müdahale gerekebilir.");
                    } else if (ort >= 70 && ort <= 110) {
                        mesaj.append(" | Uyarı Yok: Kan şekeri seviyesi normal aralıkta. Hiçbir işlem gerekmez.");
                    } else if (ort >= 111 && ort <= 150) {
                        mesaj.append(" | Takip Uyarısı: Hastanın kan şekeri 111-150 mg/dL arasında. Durum izlenmeli.");
                    } else if (ort >= 151 && ort <= 200) {
                        mesaj.append(" | İzleme Uyarısı: Hastanın kan şekeri 151-200 mg/dL arasında. Diyabet kontrolü gereklidir.");
                    } else if (ort > 200) {
                        mesaj.append(" | Acil Müdahale Uyarısı: Hastanın kan şekeri 200 mg/dL'nin üzerinde. Hiperglisemi durumu. Acil müdahale gerekebilir.");
                    }

                    uyarilar.add(mesaj.toString());
                }
            }
        }
        return uyarilar;
    }

    private boolean HastaAdminTarafindanEklenmisMi(String adminTcNo, String hastaTcNo) {
        String sql = "SELECT * FROM admin_hasta WHERE admin_tc_no = ? AND hasta_tc_no = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, adminTcNo);
            pstmt.setString(2, hastaTcNo);
            ResultSet rs = pstmt.executeQuery();
            return rs.next(); // varsa true döner
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

}
