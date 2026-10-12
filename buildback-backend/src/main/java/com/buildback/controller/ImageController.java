package com.buildback.controller;

import java.time.Duration;
import java.util.Map;

import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.buildback.service.ImageStorageService;

/**
 * Listing photos.
 *  POST /api/seller/images  (sellers only, multipart field "file") -> {"url": "/api/images/..."}
 *  GET  /api/images/{name}  (public) -> the photo itself
 *
 * Uploading only stores the file. It becomes part of a listing when the seller
 * saves the listing form with that URL in "imageUrls".
 */
@RestController
public class ImageController {

    private final ImageStorageService storage;

    public ImageController(ImageStorageService storage) {
        this.storage = storage;
    }

    @PostMapping("/api/seller/images")
    public ResponseEntity<Map<String, String>> upload(@RequestParam("file") MultipartFile file) {
        String url = storage.store(file);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("url", url));
    }

    @GetMapping("/api/images/{name}")
    public ResponseEntity<Resource> get(@PathVariable String name) {
        return storage.load(name)
                .map(resource -> ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(ImageStorageService.contentType(name)))
                        // The file behind a name never changes, so browsers may cache it for a long time
                        .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable())
                        .body(resource))
                .orElse(ResponseEntity.notFound().build());
    }
}
