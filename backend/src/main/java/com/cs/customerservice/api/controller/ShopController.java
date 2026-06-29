package com.cs.customerservice.api.controller;

import com.cs.customerservice.application.tool.MallOrderReader;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/shop")
public class ShopController {

    private final MallOrderReader mall;

    public ShopController(MallOrderReader mall) {
        this.mall = mall;
    }

    @GetMapping("/products")
    public List<Map<String, Object>> products(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String cat,
            @RequestParam(required = false) Double minPrice,
            @RequestParam(required = false) Double maxPrice) {
        return mall.queryProducts(q, sort, cat, minPrice, maxPrice);
    }

    @GetMapping("/products/{id}")
    public Object product(@PathVariable Long id) {
        return mall.findProductById(id).orElse(Map.of("error", "not found"));
    }

    @GetMapping("/orders")
    public List<Map<String, Object>> orders(@RequestParam(defaultValue = "test") String user) {
        return mall.findOrdersByMemberUsername(user);
    }

    @PostMapping("/login")
    public Object login(@RequestBody Map<String, String> body) {
        var member = mall.findMemberByUsername(body.get("username"));
        if (member.isEmpty()) return Map.of("error", "用户不存在");
        var m = member.get();
        return Map.of("id", m.get("id"), "username", m.get("username"), "phone", m.get("phone"));
    }

    @PostMapping("/orders")
    public Object createOrder(@RequestBody Map<String, Object> body) {
        String username = (String) body.get("username");
        var member = mall.findMemberByUsername(username);
        if (member.isEmpty()) return Map.of("error", "用户不存在");
        Long memberId = ((Number) member.get().get("id")).longValue();
        String orderSn = mall.generateOrderSn();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) body.get("items");
        double total = items.stream().mapToDouble(i -> ((Number)i.get("price")).doubleValue() * ((Number)i.get("qty")).intValue()).sum();
        String addr = (String) body.getOrDefault("address", "");
        String phone = (String) body.getOrDefault("phone", "");
        long orderId = mall.createOrder(memberId, username, orderSn, total, items, username, phone, addr);
        return Map.of("orderId", orderId, "orderSn", orderSn, "total", total);
    }

    @PostMapping("/register")
    public Object register(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String phone = body.getOrDefault("phone", "");
        if (mall.findMemberByUsername(username).isPresent()) return Map.of("error", "用户名已存在");
        var m = mall.createMember(username, phone);
        return Map.of("id", m.get("id"), "username", m.get("username"), "phone", m.get("phone"));
    }

    @GetMapping("/addresses")
    public List<Map<String, Object>> addresses(@RequestParam String user) {
        var member = mall.findMemberByUsername(user);
        if (member.isEmpty()) return List.of();
        return mall.findAddressesByMemberId(((Number) member.get().get("id")).longValue());
    }

    @PostMapping("/addresses")
    public Object addAddress(@RequestBody Map<String, String> body) {
        String username = body.get("user");
        var member = mall.findMemberByUsername(username);
        if (member.isEmpty()) return Map.of("error", "用户不存在");
        long id = mall.addAddress(((Number) member.get().get("id")).longValue(),
                body.get("name"), body.get("phone"), body.getOrDefault("province",""),
                body.getOrDefault("city",""), body.getOrDefault("region",""), body.get("detail"));
        return Map.of("id", id);
    }

    @GetMapping("/profile")
    public Object profile(@RequestParam String user) {
        var member = mall.findMemberByUsername(user);
        if (member.isEmpty()) return Map.of("error", "用户不存在");
        var m = member.get();
        return Map.of("id", m.get("id"), "username", m.get("username"), "phone", m.get("phone"),
                "nickname", m.get("nickname") != null ? m.get("nickname") : "",
                "city", m.get("city") != null ? m.get("city") : "");
    }

    @GetMapping("/myorders")
    public List<Map<String, Object>> myOrders(@RequestParam String user) {
        var member = mall.findMemberByUsername(user);
        if (member.isEmpty()) return List.of();
        return mall.findOrdersByMemberId(((Number) member.get().get("id")).longValue());
    }
}
