package com.flowmart.product.generator;

import org.junit.jupiter.api.Test;
import java.util.HashSet;
import static org.junit.jupiter.api.Assertions.*;

class SpuCodeGeneratorTest {
    @Test
    void producesPrefixedNumericSnowflakeCodes() {
        var generator = new SpuCodeGenerator();
        var codes = new HashSet<String>();
        for (int i = 0; i < 100; i++) {
            String code = generator.next();
            assertTrue(code.matches("SPU[0-9]+"));
            assertTrue(code.length() <= 64);
            assertTrue(codes.add(code));
        }
    }
}
