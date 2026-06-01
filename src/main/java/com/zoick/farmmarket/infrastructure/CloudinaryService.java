package com.zoick.farmmarket.infrastructure;
import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.util.Map;

@Service
@Slf4j
public class CloudinaryService {
    private final Cloudinary cloudinary;
    public CloudinaryService(
            @Value("${cloudinary.cloud-name}") String cloudName,
            @Value("${cloudinary.api-key}") String apiKey,
            @Value("${cloudinary.api-secret}") String apiSecret){
        this.cloudinary= new Cloudinary(ObjectUtils.asMap("cloud_name", cloudName, "api_key", apiKey,
                "api_secret", apiSecret, "secure", true));
    }
    public String uploadImage(MultipartFile file,  String folder){
        try{
            Map<?, ?> result= cloudinary.uploader().upload(file.getBytes(), ObjectUtils.asMap("folder", folder,
                    "resource_type", "image"));
            return (String) result.get("secure_url");
        }catch(IOException e){
            log.error("Cloudinary upload failed: {}", e.getMessage());
            throw new IllegalStateException("Image upload failed. Please try again.");
        }
    }
}
