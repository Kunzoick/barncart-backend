package com.zoick.farmmarket.domain.produce;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
//simple CRUD. the only business rule is uniqueness as you cannot create two categories with the same name
public class ProduceCategoryService {
    private final ProduceCategoryRepository produceCategoryRepository;
    @Transactional(readOnly = true)
    public List<ProduceCategoryResponse> getAllCategories(){
        return produceCategoryRepository.findAll().stream().map(ProduceCategoryResponse::from).toList();
    }
    @Transactional(readOnly = true)
    public ProduceCategoryResponse getCategoryById(UUID id){
        return ProduceCategoryResponse.from(produceCategoryRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Category not found: "+ id)));
    }
    @Transactional
    public ProduceCategoryResponse createCategory(ProduceCategoryRequest request){
        if(produceCategoryRepository.existsByName(request.getName())){
            throw new IllegalArgumentException("Category already exists: "+ request.getName());
        }
        ProduceCategory category= new ProduceCategory();
        category.setName(request.getName());
        category.setRefundWindowDays(request.getRefundWindowDays());
        return ProduceCategoryResponse.from(produceCategoryRepository.save(category));
    }
    @Transactional
    public ProduceCategoryResponse updateCategory(UUID id, ProduceCategoryRequest request){
        ProduceCategory category= produceCategoryRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Category not found: "+ id));
        //only check uniqueness if name is actually changing
        if(!category.getName().equals(request.getName()) && produceCategoryRepository.existsByName(request.getName())){
            throw new IllegalArgumentException("Category already exists: "+ request.getName());
        }
        category.setName(request.getName());
        category.setRefundWindowDays(request.getRefundWindowDays());
        return ProduceCategoryResponse.from(produceCategoryRepository.save(category));
    }
}
