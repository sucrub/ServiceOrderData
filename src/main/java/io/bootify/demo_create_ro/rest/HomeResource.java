package io.bootify.demo_create_ro.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@RestController
public class HomeResource {

    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_INSTANT; // Thời gian

    @PostMapping("/demo")
    public ResponseEntity<?> createRo(@RequestBody Object body) {
        try {
            ObjectMapper mapper = new ObjectMapper();

            // Convert body sang dạng JSON string và Map để xử lý
            String serviceOrderJson = mapper.writeValueAsString(body);
            Map<String, Object> serviceOrderMap = (Map<String, Object>) body;

            // Load các file spec cần thiết từ resource folder
            String cfssJson = loadJson("data/CustomerFacingServiceSpecification.json");
            String logicalSpecJson = loadJson("data/LogicalResourceSpecification.json");
            String rfssJson = loadJson("data/ResourceFacingServiceSpecification.json");

            // Lấy danh sách ID của CustomerFacingService từ Service Order
            List<String> cfsIds = JsonPath.read(serviceOrderJson, "$.orderItem[*].service.serviceSpecification.id");

            // Với mỗi CFS, lấy danh sách các RFS Specification ID liên quan từ CFSS
            List<String> rfssIds = new ArrayList<>();
            for (String id : cfsIds) {
                List<String> matched = JsonPath.read(cfssJson, "$[?(@.id == '" + id + "')].serviceSpecRelationship[*].id");
                rfssIds.addAll(matched);
            }

            // Từ các RFS Spec ID, lấy ra danh sách resourceSpecification bên trong RFSS
            List<Object> resourceSpecs = new ArrayList<>();
            for (String id : rfssIds) {
                List<Map<String, Object>> matchedRFSS = JsonPath.read(rfssJson, "$[?(@.id == '" + id + "')].resourceSpecification");
                for (Object entry : matchedRFSS) {
                    if (entry instanceof List<?>) {
                        resourceSpecs.addAll((List<?>) entry);
                    } else {
                        resourceSpecs.add(entry);
                    }
                }
            }

            // Lấy danh sách serviceCharacteristic từ Service Order đầu vào (flatten array)
            List<List<Object>> nestedCharacteristics = JsonPath.read(serviceOrderJson, "$.orderItem[*].service.serviceCharacteristic");
            List<Object> flatCharacteristics = new ArrayList<>();
            for (List<Object> innerList : nestedCharacteristics) {
                flatCharacteristics.addAll(innerList);
            }

            // Tạo dữ liệu Resource Order dựa trên logical spec + characteristics thu được
            Map<String, Object> resourceOrder = buildResourceOrder(serviceOrderMap, logicalSpecJson, resourceSpecs, flatCharacteristics);

            return ResponseEntity.status(201).body(resourceOrder);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(400).body("Error parsing request body");
        }
    }

    // Đọc nội dung JSON từ file trong resources
    private String loadJson(String path) throws Exception {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(path)) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    // Xây dựng Resource Order trả về dựa trên danh sách resource spec
    private Map<String, Object> buildResourceOrder(Map<String, Object> serviceOrderMap, String logicalSpecJson,
                                                   List<Object> resourceSpecs, List<Object> characteristics) {

        Map<String, Object> ro = new HashMap<>();
        ZonedDateTime now = ZonedDateTime.now(java.time.ZoneOffset.UTC);
        String nowStr = now.format(ISO_FORMATTER);

        // Thông tin chung
        ro.put("id", "ROM-1");
        ro.put("name", "");
        ro.put("category", serviceOrderMap.get("category"));
        ro.put("description", serviceOrderMap.get("description"));
        ro.put("priority", serviceOrderMap.get("priority"));
        ro.put("state", "acknowledgement");
        ro.put("expectedCompletionDate", nowStr);
        ro.put("orderDate", nowStr);
        ro.put("requestedCompletionDate", nowStr);
        ro.put("requestedStartDate", nowStr);
        ro.put("startDate", nowStr);

        // Danh sách orderItem tương ứng với từng resourceSpec
        List<Map<String, Object>> orderItems = new ArrayList<>();
        for (Object specObj : resourceSpecs) {
            Map<String, Object> specMap = (Map<String, Object>) specObj;
            String id = (String) specMap.get("id");

            // Tra cứu thông tin chi tiết từ logical spec theo ID
            List<Map<String, Object>> matchedRes = JsonPath.read(logicalSpecJson, "$[?(@.id == '" + id + "')]");
            if (matchedRes.isEmpty()) continue;

            Map<String, Object> logicalRes = matchedRes.get(0);

            // resourceSpecification
            Map<String, Object> resourceSpec = new HashMap<>();
            resourceSpec.put("id", logicalRes.get("id"));
            resourceSpec.put("name", logicalRes.get("name"));
            resourceSpec.put("@baseType", logicalRes.get("@baseType"));
            resourceSpec.put("@type", logicalRes.get("@type"));

            // resource bên trong orderItem
            Map<String, Object> resource = new HashMap<>();
            resource.put("id", "");
            resource.put("name", logicalRes.get("name"));
            resource.put("category", logicalRes.get("category"));
            resource.put("resourceSpecification", resourceSpec);
            resource.put("resourceCharacteristic", characteristics); // hiện tại lấy từ ServiceOrder
            resource.put("@baseType", "Resource");
            resource.put("@type", "LogicalResource");

            // Tạo orderItem hoàn chỉnh
            Map<String, Object> item = new HashMap<>();
            item.put("id", "");
            item.put("quantity", 1);
            item.put("action", "add");
            item.put("state", "acknowledgement");
            item.put("resource", resource);

            orderItems.add(item);
        }

        ro.put("orderItems", orderItems);
        return ro;
    }
}