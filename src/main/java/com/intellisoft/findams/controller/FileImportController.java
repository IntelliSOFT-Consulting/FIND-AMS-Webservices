package com.intellisoft.findams.controller;

import com.intellisoft.findams.service.FileParsingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.Disposable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Slf4j
@RestController
@CrossOrigin(origins = "*", allowedHeaders = "*")
@RequestMapping(value = "/api/find-ams/file-import", produces = "application/json")
public class FileImportController {
    private final FileParsingService parsingService;

    public FileImportController(FileParsingService parsingService) {
        this.parsingService = parsingService;
    }

    @PostMapping("/parse-file")
    public ResponseEntity<Disposable> parseFile(@RequestParam("fileContent") MultipartFile file) throws IOException {

        String filePath = "/Users/nelsonkimaiga/Documents/IntelliSoft/findams/whonet/";

        String fileContent = new String(file.getBytes(), StandardCharsets.UTF_8);
        return ResponseEntity.status(HttpStatus.OK).body(parsingService.parseFile(filePath, fileContent));
    }
}
