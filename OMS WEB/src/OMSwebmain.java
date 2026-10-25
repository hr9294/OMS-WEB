import java.io.IOException;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;

public class OMSwebmain {

    static WebDriver driver;

    public static void main(String[] args) throws InterruptedException, IOException {

        //System.setProperty("webdriver.chrome.driver",
           // "D:\\Automation & Load Testing\\Selenium\\Software\\152.0.7977.64 chromedriver-win64\\chromedriver-win64\\chromedriver.exe");

        ChromeOptions options = new ChromeOptions();

        // Hide automation flags
        options.setExperimentalOption("excludeSwitches", new String[]{"enable-automation"});
        options.setExperimentalOption("useAutomationExtension", false);
        options.addArguments("--disable-blink-features=AutomationControlled");
        options.addArguments("--disable-infobars");

        // Privacy tweaks: disable password manager, notifications, etc.
        Map<String, Object> prefs = new HashMap<>();
        prefs.put("credentials_enable_service", false);
        prefs.put("profile.password_manager_enabled", false);
        prefs.put("safebrowsing.enabled", false);
        prefs.put("profile.default_content_setting_values.notifications", 2);
        options.setExperimentalOption("prefs", prefs);
        options.setAcceptInsecureCerts(true);

        driver = new ChromeDriver(options);
        driver.manage().window().maximize();
        driver.manage().deleteAllCookies();
        driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(20));
        driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(10));

        driver.get("https://devoms.quantbd.com");
        Thread.sleep(1000);

        ((org.openqa.selenium.JavascriptExecutor) driver).executeScript(
            "Object.defineProperty(navigator, 'webdriver', {get: () => undefined})"
        );

        System.out.println("Welcome to The UFTFAST Order Management System Application");

        Scanner sc = new Scanner(System.in);
        
        

        // Login
        OMSweb_Login.main(driver, sc);

        // Trade Page 
        OMSweb_Trade.main(driver, sc);

        
        // OMSweb_MultipleTrade.main(driver, sc);
        // Position.main(driver, sc);
        // ClientPortfolio.main(driver, sc);
        // Order_Summary.main(driver, sc);
        // Account_Limits.main(driver, sc);
        // Market_Depth.main(driver, sc);
        // Order_Terminal.main(driver, sc);

        System.out.println("\n[UFTFAST OMS] Trade session completed. Exiting application.");
        sc.close();
    }
}