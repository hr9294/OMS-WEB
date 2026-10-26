import java.util.Scanner;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.JavascriptExecutor;
import java.util.List;
import java.awt.Robot;
import java.awt.event.KeyEvent;


public class OMSweb_Login {

	public static void main(WebDriver driver, Scanner sc) throws InterruptedException {
		// TODO Auto-generated method stub
		
		
		WebElement userNameInput = driver.findElement(By.xpath("//input[@id='username']"));
		userNameInput.sendKeys("testadmin");
        Thread.sleep(1000);
        
		/*System.out.println("User Name: ");
    	String userName = sc.nextLine();
    	WebElement userNameInput = driver.findElement(By.xpath("//input[@id='username']"));
        userNameInput.sendKeys(userName);
    	Thread.sleep(1000);*/

        //Enter Password
        WebElement passwordInput = driver.findElement(By.xpath("//input[@id='password']"));
        passwordInput.sendKeys("12345");
    	Thread.sleep(1000);
        
        /*System.out.println("Password: ");
    	String password = sc.nextLine();
    	WebElement passwordInput = driver.findElement(By.xpath("//input[@id='password']"));
    	passwordInput.sendKeys(password);
    	Thread.sleep(1000);*/
        
        
        //Submit
        WebElement submit = driver.findElement(By.xpath("//button[normalize-space()='Login']"));
        submit.click();
        Thread.sleep(1000);
        
        //Active Session
        //WebElement isActiveSession =  driver.findElement(By.cssSelector(".modal-content.login-modal-content"));
        
        //boolean isActiveSession = true;
        
        if (driver.findElements(By.id("continue_to_force_login")).size() > 0) {
        	
        System.out.println("You have already an Active Session ");
		System.out.println("If \"YES\", please enter: \"1\" ");
		System.out.println("Otherwise, please enter: \"0\" ");
		System.out.println("Please Enter your choise: \n");
				
		String ch = sc.nextLine();
		
		try {	
			
			int int_ch = Integer.parseInt(ch);
					
		if (int_ch==1) {
				
				
	    // You have already an Active Session 
			            
           WebElement Continue = driver.findElement(By.xpath("//button[@id='continue_to_force_login']"));
           Continue.click();
           System.out.println("Continuing active session...");
           Thread.sleep(1000);
	}
	
		else if(int_ch==0){	
			
			WebElement Cancel = driver.findElement(By.xpath("//button[@id='cancel_to_force_login']"));
	        Cancel.click();
	        System.out.println("Cancelling active session...");
	        Thread.sleep(1000);
			
		}
		
		else {
            // No active session, proceed to next step
            System.out.println("Invalid choice. Exiting...");
            
         }
		
	   }
		
		catch (NumberFormatException e) {
			
            System.out.println("Invalid input. Please enter a numeric value (0 or 1).");
        //isActiveSession = true;
	}
        
        
        }
        
        else {
            // No active session
            System.out.println("No active session popup detected. Proceeding normally...");
        }

        // Automatically press OK on breach or any system modal
        pressOkAutomatically(driver);
    }

    // =========================================================================
    // AUTOMATIC POPUP & BREACH MODAL DISMISSAL ("OK" BUTTON)
    // =========================================================================
    public static void pressOkAutomatically(WebDriver driver) {
        try {
            Thread.sleep(800); // Allow Chrome / DOM a brief moment to render popup if triggered
        } catch (InterruptedException ignored) {}

        // 1. Try DOM OK button if present in web modal
        try {
            List<WebElement> okButtons = driver.findElements(By.xpath(
                "//button[translate(normalize-space(), 'ok', 'OK')='OK'] | " +
                "//a[translate(normalize-space(), 'ok', 'OK')='OK'] | " +
                "//button[contains(translate(text(), 'ok', 'OK'), 'OK')] | " +
                "//div[contains(@class,'modal') or contains(@class,'dialog') or contains(@class,'popup')]//button"
            ));
            for (WebElement btn : okButtons) {
                if (btn.isDisplayed()) {
                    ((JavascriptExecutor) driver).executeScript("arguments[0].click();", btn);
                    System.out.println("[POPUP] Automatically clicked OK button in DOM.");
                    Thread.sleep(300);
                    return;
                }
            }
        } catch (Exception ignored) {}

        // 2. Try JavaScript browser alert if present
        try {
            driver.switchTo().alert().accept();
            System.out.println("[POPUP] Automatically accepted browser alert.");
            return;
        } catch (Exception ignored) {}

        // 3. Native Robot ENTER key press to hit focused 'OK' button on Chrome native breach modal
        try {
            Robot robot = new Robot();
            robot.delay(150);
            robot.keyPress(KeyEvent.VK_ENTER);
            robot.keyRelease(KeyEvent.VK_ENTER);
            System.out.println("[POPUP] Automatically pressed OK (ENTER key) on popup.");
        } catch (Exception e) {
            System.out.println("[POPUP] Note sending automatic OK: " + e.getMessage());
        }
    }
}
