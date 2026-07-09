# ADR-010 — Image Upload: Cloudinary HTTP Call Outside Transaction

## Status
Accepted

## Context
`ProduceService.uploadImage()` needs to upload a file to Cloudinary then save the returned URL to the database. The naive implementation wraps both in `@Transactional` — but this holds a DB connection open during the Cloudinary HTTP upload, which can take 2–10 seconds for images up to 5MB.

An alternative was considered: a private `saveImageUrl()` helper with its own `@Transactional` called from within the upload method. This approach is silently broken — Spring's `@Transactional` is implemented via AOP proxies, which only intercept calls from outside the object. An internal `this.saveImageUrl()` call bypasses the proxy entirely and runs without a transaction, with no error thrown.

## Decision
`uploadImage()` has no `@Transactional` annotation. It runs two sequential operations:

1. Call Cloudinary — returns a URL
2. Call `produceRepository.save()` — auto-transaction via Spring Data

```java
// No @Transactional — deliberate
public void uploadImage(UUID produceId, MultipartFile file) {
    String imageUrl = cloudinaryService.upload(file);  // HTTP call, no transaction
    Produce produce = produceRepository.findById(produceId)
        .orElseThrow(() -> new IllegalArgumentException("Produce not found"));
    produce.setImageUrl(imageUrl);
    produceRepository.save(produce);  // auto-transaction
}
```

## Consequences
- DB connection is not held during Cloudinary upload — no connection pool pressure from slow uploads
- The internal `@Transactional` anti-pattern is avoided — the method is correctly non-transactional
- **Known gap:** If Cloudinary upload succeeds but `produceRepository.save()` fails (e.g. DB unavailable), the image is orphaned in Cloudinary with no DB reference. Production fix: a scheduled cleanup job that queries Cloudinary for all uploaded images and removes those with no matching `image_url` in the database
- **Known gap:** `file.getBytes()` loads the entire file into the Java heap before upload. For a 5MB image on a 512MB instance this is acceptable but not ideal. Production fix: streaming upload using Cloudinary's `InputStream` API
- Content-type validation relies on the file's declared MIME type, which can be spoofed. Magic byte validation (reading the first bytes of the file to verify actual format) is not implemented
