package dev.lacre;

import org.springframework.boot.SpringApplication;

public class TestLacreApplication {

	public static void main(String[] args) {
		SpringApplication.from(LacreApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
