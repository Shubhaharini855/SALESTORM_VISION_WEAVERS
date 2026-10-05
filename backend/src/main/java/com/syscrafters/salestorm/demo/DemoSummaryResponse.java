package com.syscrafters.salestorm.demo;

import com.syscrafters.salestorm.inventory.dto.InventorySummaryResponse;

public record DemoSummaryResponse(int initialStock,
                                 InventorySummaryResponse inventory,
                                 long totalReservations,
                                 long successfulReservations,
                                 long failedPayments,
                                 long releasedReservations,
                                 long ordersPendingRecovery,
                                 long overselling) {
}