package com.zoick.farmmarket.infrastructure.websocket;
import java.util.UUID;
public record InventoryUpdateEvent(UUID batchId) {}
