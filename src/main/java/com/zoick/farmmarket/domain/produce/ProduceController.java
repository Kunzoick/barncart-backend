package com.zoick.farmmarket.domain.produce;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/produce")
@RequiredArgsConstructor
//Public Get endpoints for browsing
public class ProduceController {
    private final ProduceService produceService;

    //public for anyone to see active produce
    @GetMapping
    public ResponseEntity<List<ProduceResponse>> getAllActiveProduce() {
        return ResponseEntity.ok(produceService.getAllActiveProduce());
    }
    @GetMapping("/{id}")
    public ResponseEntity<ProduceResponse> getProduceById(@PathVariable UUID id) {
        return ResponseEntity.ok(produceService.getProduceById(id));
    }
    @GetMapping("/category/{categoryId}")
    public ResponseEntity<List<ProduceResponse>> getProduceByCategory(@PathVariable UUID categoryId) {
        return ResponseEntity.ok(produceService.getProduceByCategory(categoryId));
    }
    //admin only
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ProduceResponse> createProduce(@Valid @RequestBody CreateProduceRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(produceService.createProduce(request));
    }
    @PostMapping(value = "/{id}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ProduceResponse> uploadImage(@PathVariable UUID id, @RequestParam("file")MultipartFile file){
        if(file.isEmpty()){
            throw new IllegalArgumentException("File cannot be empty");
        }
        String contentType= file.getContentType();
        if(contentType == null || !contentType.startsWith("image/")){
            throw new IllegalArgumentException("File must be an image");
        }
        return ResponseEntity.ok(produceService.uploadImage(id, file));
    }
    @PatchMapping("/{id}/deactivate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ProduceResponse> deactivateProduce(@PathVariable UUID id) {
        return ResponseEntity.ok(produceService.deactivateProduce(id));
    }
}

