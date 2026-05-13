package org.example;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class Main {
    public static void main(String[] args) {
        Dotenv.configure().ignoreIfMissing().load()
            .entries()
            .forEach(e -> System.setProperty(e.getKey(), e.getValue()));

        SpringApplication.run(Main.class, args);
    }
}
