package com.finance;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.finance.entity.User;
import com.finance.enums.Role;
import com.finance.repo.UserRepository;

@SpringBootApplication
public class FinanceManagementSystemApplication {

	public static void main(String[] args) {
		SpringApplication.run(FinanceManagementSystemApplication.class, args);
	}

}
