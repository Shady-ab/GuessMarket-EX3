package guessmarket.dto;

import java.util.List;

public record BookDto(Double last, Double bestBid, Double bestAsk, Double mid, Double spread,
                      List<OrderDto> bids, List<OrderDto> asks) {
}
