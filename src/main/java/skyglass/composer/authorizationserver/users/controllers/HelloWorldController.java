package skyglass.composer.authorizationserver.users.controllers;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import skyglass.composer.authorizationserver.users.entities.SimpleMessage;

@RestController
public class HelloWorldController {

	@GetMapping("/hello")
	public String simpleHello() {
		return "Hello World - V1";
	}

	@GetMapping("/hello-bean")
	public SimpleMessage helloBean() {
		return new SimpleMessage("Hello World Bean", "V1");
	}

}
