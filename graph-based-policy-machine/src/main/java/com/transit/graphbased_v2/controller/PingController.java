package com.transit.graphbased_v2.controller;

import com.transit.graphbased_v2.controller.dto.ReturnPingDTO;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/ping")
@Tag(name = "Ping", description = "Just for testing if api is ready to use")
public class PingController {

    @GetMapping()
    public ResponseEntity pingpong() {
        var temp = new ReturnPingDTO();
        temp.setPing("pong");
        return new ResponseEntity(temp, HttpStatus.OK);
    }
}
