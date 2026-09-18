package com.codewithalanso.shopforge.controllers;



import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;

@RestController
public class HomeController {

    @GetMapping("/")
    public Map<String, Object> home() {
        return Map.of(
            "app", "ShopForge API",
            "status", "ACTIVE",
            "message", "Welcome to ShopForge E-commerce Platform REST API",
            "version", "0.0.1-SNAPSHOT"
        );
    }
}
