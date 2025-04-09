package io.bootify.demo_create_ro.rest;

import com.jayway.jsonpath.JsonPath;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


@RestController
public class HomeResource {

    @PostMapping("/demo")
    public ResponseEntity<?> createRo(@RequestBody Object body) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            // INPUT SERVICE ORDER
            String serviceOrder = objectMapper.writeValueAsString(body);
            Map<String, Object> serviceOrderBody = (Map<String, Object>) body;

            InputStream isCFSS = getClass().getClassLoader().getResourceAsStream("data/CustomerFacingServiceSpecification.json");
            String customerFacingServiceSpecification = new String(isCFSS.readAllBytes(), StandardCharsets.UTF_8);

            InputStream isLogicalRSpec = getClass().getClassLoader().getResourceAsStream("data/LogicalResourceSpecification.json");
            String logicalResourceSpecification = new String(isLogicalRSpec.readAllBytes(), StandardCharsets.UTF_8);

            // LẤY DANH SÁCH ID CFS
            List<String> serviceSpecIds = JsonPath.read(serviceOrder, "$.orderItem[*].service.serviceSpecification.id");

            // LẤY CFSS TƯƠNG ỨNG DỰA THEO DANH SÁCH ID CFS BÊN TRÊN
            List<Object> matchedCFSSpecifications = new ArrayList<>();

            for (String id : serviceSpecIds) {
                List<Object> matched = JsonPath.read(customerFacingServiceSpecification,
                        "$[?(@.id == '" + id + "')]");
                matchedCFSSpecifications.addAll(matched);
            }

            String matchedCFSSString = objectMapper.writeValueAsString(matchedCFSSpecifications);
            // LẤY DANH SÁCH RFS ID
            List<String> rfsIds = JsonPath.read(matchedCFSSString, "$.*.serviceSpecRelationship[*].id");
            System.out.println(rfsIds);

            // LẤY DANH SÁCH RFSS TƯƠNG ỨNG VỚI ID BÊN TRÊN
            List<Object> matchedRFSSpecifications = new ArrayList<>();
            InputStream isRFSS = getClass().getClassLoader().getResourceAsStream("data/ResourceFacingServiceSpecification.json");
            String resourceFacingServiceSpecification = new String(isRFSS.readAllBytes(), StandardCharsets.UTF_8);
            for (String id : rfsIds) {
                List<Object> matched = JsonPath.read(resourceFacingServiceSpecification,
                        "$[?(@.id == '" + id + "')]");
                matchedRFSSpecifications.addAll(matched);
            }

            String matchedRFSSpecificationsString = objectMapper.writeValueAsString(matchedRFSSpecifications);
            List<Object> RFSSpecData = JsonPath.read(matchedRFSSpecificationsString, "$.*.resourceSpecification");
            List<Object> RFSSpec = new ArrayList<>();
            for (Object sublist : RFSSpecData) {
                if (sublist instanceof List<?>) {
                    RFSSpec.addAll((List<?>) sublist);
                } else {
                    RFSSpec.add(sublist);
                }
            }

            // THIS IS THE LIST OF RFSPEC NEED TO BE WORKING ON
            System.out.println(RFSSpec);

            // BUILD RESOURCE ORDER
            Map<String, Object> resourceOrder = new HashMap<>();
            resourceOrder.put("id", "ROM-1");
            resourceOrder.put("name", "");
            resourceOrder.put("category", serviceOrderBody.get("category"));
            resourceOrder.put("description", serviceOrderBody.get("description"));
            resourceOrder.put("priority", serviceOrderBody.get("priority"));
            resourceOrder.put("state", "acknowledgement");
            resourceOrder.put("expectedCompletionDate", ZonedDateTime.now(java.time.ZoneOffset.UTC)
                    .format(DateTimeFormatter.ISO_INSTANT));
            resourceOrder.put("orderDate", ZonedDateTime.now(java.time.ZoneOffset.UTC)
                    .format(DateTimeFormatter.ISO_INSTANT));
            resourceOrder.put("requestedCompletionDate", ZonedDateTime.now(java.time.ZoneOffset.UTC)
                    .format(DateTimeFormatter.ISO_INSTANT));
            resourceOrder.put("requestedStartDate", ZonedDateTime.now(java.time.ZoneOffset.UTC)
                    .format(DateTimeFormatter.ISO_INSTANT));
            resourceOrder.put("startDate", ZonedDateTime.now(java.time.ZoneOffset.UTC)
                    .format(DateTimeFormatter.ISO_INSTANT));

            // BUILD ORDER ITEMS
            List<Map<String, Object>> orderItems = new ArrayList<>();
            for(Object resourceItems : RFSSpec) {
                Map<String, Object> resourceItemsBody = (Map<String, Object>) resourceItems;
                List<Object> matched = JsonPath.read(logicalResourceSpecification,
                        "$[?(@.id == '" + resourceItemsBody.get("id") + "')]");
                Map<String, Object> logicalRes = (Map<String, Object>) matched.get(0);
                // get specific resource
                Map<String, Object> resourceItemsObject = new HashMap<>();
                resourceItemsObject.put("id", "");
                resourceItemsObject.put("quantity", 1);
                resourceItemsObject.put("action", "add");
                resourceItemsObject.put("state", "acknowledgement");
                Map<String, Object> resource = new HashMap<>();
                resource.put("id", "");
                resource.put("name", logicalRes.get("name"));
                resource.put("category", logicalRes.get("category"));

                // res specification here
                Map<String, Object> resourceSpecification = new HashMap<>();
                resourceSpecification.put("id", logicalRes.get("id"));
                resourceSpecification.put("name", logicalRes.get("name"));
                resourceSpecification.put("@baseType", logicalRes.get("@baseType"));
                resourceSpecification.put("@type", logicalRes.get("@type"));

                resource.put("resourceSpecification", resourceSpecification);

                // res characteristic here // Tạm thời lấy từ body luôn
                List<Map<String, Object>> resourceCharacteristic = new ArrayList<>();
                resource.put("resourceCharacteristic", resourceCharacteristic);

                /// /// /// /// ///
                resource.put("@baseType", "Resource");
                resource.put("@type", "LogicalResource");


                resourceItemsObject.put("resource", resource);
                orderItems.add(resourceItemsObject);
            }
            resourceOrder.put("orderItems", orderItems);


            return ResponseEntity.status(201).body(resourceOrder);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(400).body("Error parsing request body");
        }
    }

}
