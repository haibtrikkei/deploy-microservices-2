package ra.productservice.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/products")
public class ProductController {
    @GetMapping
    public ResponseEntity<List<String>> getProducts() {
        List<String> products = List.of("Product 1", "Product 2", "Product 3");
        return ResponseEntity.ok(products);
    }
}
