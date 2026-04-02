package ie.ucd.bdic.group6.facade.handler;

import ie.ucd.bdic.group6.command.TradeCommand;
import ie.ucd.bdic.group6.facade.model.GameResult;

public class TradeHandler implements CommandHandler<TradeCommand> {
    @Override
    public Class<TradeCommand> commandType() {
        return TradeCommand.class;
    }

    @Override
    public GameResult handle(TradeCommand command) {
        return GameResult.notImplemented("TRADE");
    }
}

