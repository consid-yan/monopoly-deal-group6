package ie.ucd.bdic.group6.core.engine;

import ie.ucd.bdic.group6.command.*;

import java.util.Map;

public interface ActionExecutor {
    Map<String, Object> executeCreateSession(CreateSessionCommand command);
    Map<String, Object> executeJoinSession(JoinSessionCommand command);
    Map<String, Object> executeStartGame(StartGameCommand command);
    Map<String, Object> executePlayCard(PlayCardCommand command);
    Map<String, Object> executePayDebt(PayDebtCommand command);
    Map<String, Object> executeProposeTrade(ProposeTradeCommand command);
    Map<String, Object> executeRespondTrade(RespondTradeCommand command);
    Map<String, Object> executeRespondAction(RespondActionCommand command);
    Map<String, Object> executeDiscardCards(DiscardCardsCommand command);
    Map<String, Object> executeEndTurn(EndTurnCommand command);
    Map<String, Object> executeRejoinSession(RejoinSessionCommand command);
    Map<String, Object> executePlayerDisconnected(PlayerDisconnectedCommand command);
    Map<String, Object> executeProcessDisconnectTimeouts(ProcessDisconnectTimeoutsCommand command);
}
