package com.zoick.farmmarket.domain.produce;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/categories")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
//Admin only-> no public access
public class ProduceCategoryController {
   private final ProduceCategoryService produceCategoryService;

   @GetMapping
    public ResponseEntity<List<ProduceCategoryResponse>> getAllCategories(){
       return ResponseEntity.ok(produceCategoryService.getAllCategories());
   }
   @GetMapping("/{id}")
    public ResponseEntity<ProduceCategoryResponse> getCategoryById(@PathVariable UUID id){
        return ResponseEntity.ok(produceCategoryService.getCategoryById(id));
    }
    @PostMapping
    public ResponseEntity<ProduceCategoryResponse> createCategory(@Valid @RequestBody ProduceCategoryRequest request){
        return ResponseEntity.status(HttpStatus.CREATED).body(produceCategoryService.createCategory(request));
    }
    @PutMapping("/{id}")
    public ResponseEntity<ProduceCategoryResponse>  updateCategory(@PathVariable UUID id, @Valid @RequestBody ProduceCategoryRequest request){
        return ResponseEntity.ok(produceCategoryService.updateCategory(id, request));
    }
}
