import java.util.Scanner;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;


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
	
       
        //sc.close();
      
}
	}
