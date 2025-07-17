package dev.openfeature.contrib.testclasses;

import java.util.Map;
import lombok.Data;

@Data
public class OfrepRequestTest {
    public Map<String, Object> context;

    public OfrepRequestTest() {}
}
