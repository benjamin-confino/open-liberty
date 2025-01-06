package spring.test.init.jar;


import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;

import org.springframework.web.WebApplicationInitializer;

public class JarInit implements WebApplicationInitializer {

    @Override
    public void onStartup(ServletContext servletContext) throws ServletException {      
        System.out.println("onStartup method found via jar file");
    }

}
