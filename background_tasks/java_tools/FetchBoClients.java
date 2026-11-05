import java.time.Duration;
import java.util.*;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.*;

public class FetchBoClients {
    public static void main(String[] args) {
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless=new");
        WebDriver driver = new ChromeDriver(options);
        try {
            driver.get("https://devoms.quantbd.com");
            Thread.sleep(1000);
            driver.findElement(By.id("username")).sendKeys("Testadmin");
            driver.findElement(By.id("password")).sendKeys("12345");
            driver.findElement(By.xpath("//button[normalize-space()='Login']")).click();
            Thread.sleep(1500);

            OMSweb_Login.pressOkAutomatically(driver);
            Thread.sleep(1000);

            JavascriptExecutor js = (JavascriptExecutor) driver;

            Object res = js.executeScript(
                "var out = [];\n" +
                "$.ajax({\n" +
                "    url: '/bo_accounts/client_list?limit=50&page=1',\n" +
                "    async: false,\n" +
                "    success: function(data) {\n" +
                "        var div = document.createElement('div');\n" +
                "        div.innerHTML = data;\n" +
                "        var rows = Array.from(div.querySelectorAll('table tbody tr'));\n" +
                "        rows.forEach(function(r) {\n" +
                "            var c = Array.from(r.querySelectorAll('td')).map(function(td) { return td.innerText.trim(); });\n" +
                "            if (c.length >= 10) {\n" +
                "                out.push({\n" +
                "                    clientCode: c[1],\n" +
                "                    dealerId: c[2],\n" +
                "                    boid: c[4],\n" +
                "                    name: c[5],\n" +
                "                    address: c[6],\n" +
                "                    contact: c[7],\n" +
                "                    icNo: c[8],\n" +
                "                    accountType: c[9],\n" +
                "                    netAdjustment: c[10],\n" +
                "                    shortSelling: c.length > 11 ? c[11] : 'No'\n" +
                "                });\n" +
                "            }\n" +
                "        });\n" +
                "    }\n" +
                "});\n" +
                "return out;"
            );

            System.out.println("Fetched " + ((List<?>) res).size() + " client records from DB bo_accounts/client_list table!");
            List<?> list = (List<?>) res;
            for (int i = 0; i < Math.min(10, list.size()); i++) {
                System.out.println(list.get(i));
            }

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            driver.quit();
        }
    }
}
