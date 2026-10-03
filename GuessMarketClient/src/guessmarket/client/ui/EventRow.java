package guessmarket.client.ui;

import guessmarket.dto.EventSummaryDto;

/** Table row of the events screen (carried over from Exercise 2, now built from a DTO instead of the engine). */
public final class EventRow {
    private final EventSummaryDto dto;

    EventRow(EventSummaryDto dto) {
        this.dto = dto;
    }

    EventSummaryDto dto() {
        return dto;
    }

    public int getEventId() {
        return dto.id();
    }

    public String getId() {
        return String.valueOf(dto.id());
    }

    public String getName() {
        return dto.name();
    }

    public String getStatus() {
        return dto.status();
    }

    public String getType() {
        return dto.type();
    }

    public String getOptions() {
        return String.valueOf(dto.optionCount());
    }

    public String getCommission() {
        return UiFormat.commission(dto.commission(), dto.commissionType());
    }

    public String getAccount() {
        return UiFormat.money(dto.account());
    }

    public String getMarketMaker() {
        return dto.marketMaker();
    }

    public String getWinner() {
        return dto.winner() == null ? "" : dto.winner();
    }
}
