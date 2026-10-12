package com.buildback.service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.buildback.exception.InvalidRequestException;

/**
 * Saves listing photos uploaded by sellers and reads them back.
 *
 * Safety rules:
 *  - the file TYPE is decided from the first bytes of the file ("magic numbers"),
 *    never from the file name or the browser's Content-Type, which can lie;
 *  - only JPG, PNG and WebP are accepted (no SVG: it can contain scripts);
 *  - the server picks a random file name, so a user can never choose a path
 *    like "../../something" and write outside the uploads folder;
 *  - reading only accepts names in exactly that random format.
 */
@Service
public class ImageStorageService {

    /** Public address of an uploaded photo: /api/images/{name} */
    public static final String URL_PREFIX = "/api/images/";

    public static final long MAX_BYTES = 5L * 1024 * 1024; // 5 MB

    /** 32 hex characters (a UUID without dashes) + extension, e.g. 3f2a...9c.jpg */
    private static final Pattern STORED_NAME = Pattern.compile("[0-9a-f]{32}\\.(jpg|png|webp)");

    private final Path folder;

    public ImageStorageService(@Value("${app.uploads.dir:uploads}") String uploadsDir) {
        this.folder = Path.of(uploadsDir, "materials").toAbsolutePath().normalize();
        try {
            Files.createDirectories(folder);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot create the uploads folder " + folder, e);
        }
    }

    /** Saves the photo and returns its public URL, e.g. /api/images/3f2a...9c.jpg */
    public String store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw invalid("Choose a photo to upload");
        }
        if (file.getSize() > MAX_BYTES) {
            throw invalid("Each photo must be 5 MB or smaller");
        }
        try {
            byte[] bytes = file.getBytes();
            String extension = detectExtension(bytes);
            if (extension == null) {
                throw invalid("Only JPG, PNG or WebP photos are allowed");
            }
            String name = UUID.randomUUID().toString().replace("-", "") + "." + extension;
            // CREATE_NEW: never overwrite an existing file
            Files.write(folder.resolve(name), bytes, StandardOpenOption.CREATE_NEW);
            return URL_PREFIX + name;
        } catch (IOException e) {
            throw new UncheckedIOException("Could not save the uploaded photo", e);
        }
    }

    /** The stored photo, or empty if the name is not in our format or the file does not exist. */
    public Optional<Resource> load(String name) {
        if (name == null || !STORED_NAME.matcher(name).matches()) {
            return Optional.empty();
        }
        Path path = folder.resolve(name);
        return Files.isRegularFile(path) ? Optional.of(new FileSystemResource(path)) : Optional.empty();
    }

    /** True for "/api/images/{name}" where name has our random format (the file may not exist). */
    public static boolean isUploadedImageUrl(String url) {
        return url != null && url.startsWith(URL_PREFIX)
                && STORED_NAME.matcher(url.substring(URL_PREFIX.length())).matches();
    }

    /** True if an uploaded-photo URL points to a file that really exists. */
    public boolean exists(String url) {
        return isUploadedImageUrl(url) && load(url.substring(URL_PREFIX.length())).isPresent();
    }

    public static String contentType(String name) {
        if (name.endsWith(".png")) {
            return "image/png";
        }
        if (name.endsWith(".webp")) {
            return "image/webp";
        }
        return "image/jpeg";
    }

    /** Looks at the file signature (first bytes). Returns null for anything else. */
    static String detectExtension(byte[] b) {
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return "jpg";
        }
        if (b.length >= 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G'
                && b[4] == 0x0D && b[5] == 0x0A && b[6] == 0x1A && b[7] == 0x0A) {
            return "png";
        }
        if (b.length >= 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') {
            return "webp";
        }
        return null;
    }

    private static InvalidRequestException invalid(String message) {
        return new InvalidRequestException(Map.of("file", message));
    }
}
