package com.intellisoft.findams.controller;

import com.intellisoft.findams.constants.Constants;
import com.intellisoft.findams.service.MicrobiologyService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.Disposable;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Objects;

@Slf4j
@RestController
@CrossOrigin(origins = "*", allowedHeaders = "*")
@RequestMapping(value = "/ams/file-import", produces = "application/json")
public class FileImportController {
    private final MicrobiologyService parsingService;

    public FileImportController(MicrobiologyService parsingService) {
        this.parsingService = parsingService;
    }

    @PostMapping("/parse-file")
    public ResponseEntity<Disposable> parseFile(@RequestParam("fileContent") MultipartFile file) throws IOException {

        String fileName = StringUtils.cleanPath(Objects.requireNonNull(file.getOriginalFilename()));
        String filePath = Constants.WHONET_FILE_PATH + File.separator + fileName;

        // Save the uploaded file to the specified path
        Path destinationPath = Paths.get(filePath);
        Files.copy(file.getInputStream(), destinationPath, StandardCopyOption.REPLACE_EXISTING);

        // Process the uploaded file
        String fileContent = new String(file.getBytes(), StandardCharsets.UTF_8);
        return ResponseEntity.status(HttpStatus.OK).body(parsingService.parseFile(filePath, fileContent, fileName));
    }


}
