package com.workbench.workshop.infrastructure.persistence;

import com.workbench.workshop.domain.service.ServiceStatusEnum;
import com.workbench.workshop.domain.service.Status;
import com.workbench.workshop.infrastructure.persistence.service.ServiceStatusConverter;
import com.workbench.workshop.infrastructure.persistence.serviceorder.ListToJsonConverter;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ConvertersTest {

    private final ListToJsonConverter listConverter = new ListToJsonConverter();
    private final ServiceStatusConverter statusConverter = new ServiceStatusConverter();

    @Test
    void listToJsonRoundTrip() {
        List<String> list = List.of("TROCA_OLEO", "ALINHAMENTO");

        String json = listConverter.convertToDatabaseColumn(list);
        List<String> back = listConverter.convertToEntityAttribute(json);

        assertEquals(list, back);
    }

    @Test
    void listToJsonHandlesNullAndEmpty() {
        assertEquals("[]", listConverter.convertToDatabaseColumn(null));
        assertEquals("[]", listConverter.convertToDatabaseColumn(List.of()));
        assertTrue(listConverter.convertToEntityAttribute(null).isEmpty());
        assertTrue(listConverter.convertToEntityAttribute("[]").isEmpty());
    }

    @Test
    void serviceStatusRoundTrip() {
        List<Status> statuses = List.of(new Status(ServiceStatusEnum.TO_DO, LocalDateTime.now()));

        String json = statusConverter.convertToDatabaseColumn(statuses);
        List<Status> back = statusConverter.convertToEntityAttribute(json);

        assertEquals(1, back.size());
        assertEquals(ServiceStatusEnum.TO_DO, back.get(0).getStatus());
    }

    @Test
    void serviceStatusHandlesNullAndEmpty() {
        assertEquals("[]", statusConverter.convertToDatabaseColumn(null));
        assertEquals("[]", statusConverter.convertToDatabaseColumn(List.of()));
        assertTrue(statusConverter.convertToEntityAttribute(null).isEmpty());
        assertTrue(statusConverter.convertToEntityAttribute("[]").isEmpty());
    }
}
