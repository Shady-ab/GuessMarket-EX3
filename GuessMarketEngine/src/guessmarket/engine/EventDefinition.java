package guessmarket.engine;

import java.util.List;

/** A validated event exactly as it was described in an uploaded XML file, before it gets an id and a market maker. */
public record EventDefinition(String name, String description, int commission, CommissionType commissionType,
                              List<String> options, MarketType marketType, Double b, Integer initial, Integer d,
                              boolean allowMint) {
}
