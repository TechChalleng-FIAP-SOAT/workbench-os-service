package com.workbench.workshop.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import lombok.Data;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UpperCaseDeserializerTest {

    @Data
    static class Sample {
        @UpperCase
        private String plate;
        private String name;
    }

    private ObjectMapper mapperWithModule() {
        ObjectMapper mapper = new ObjectMapper();
        SimpleModule module = new SimpleModule();
        module.addDeserializer(String.class, new UpperCaseDeserializer());
        mapper.registerModule(module);
        return mapper;
    }

    @Test
    void upperCasesAnnotatedField() throws Exception {
        Sample s = mapperWithModule().readValue("{\"plate\":\" abc1234 \",\"name\":\"joão\"}", Sample.class);

        assertEquals("ABC1234", s.getPlate());
        // Campo sem @UpperCase não é alterado
        assertEquals("joão", s.getName());
    }

    @Test
    void handlesNullValue() throws Exception {
        Sample s = mapperWithModule().readValue("{\"plate\":null}", Sample.class);
        assertNull(s.getPlate());
    }

    @Test
    void jacksonUpperCaseConfigProvidesModule() {
        assertNotNull(new JacksonUpperCaseConfig().upperCaseModule());
    }
}
