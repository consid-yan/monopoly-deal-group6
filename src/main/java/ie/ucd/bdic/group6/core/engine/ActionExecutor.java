package ie.ucd.bdic.group6.core.engine;

import ie.ucd.bdic.group6.command.*;
import ie.ucd.bdic.group6.core.snapshot.SessionSnapshot;

import java.util.Map;

public interface ActionExecutor {
    SessionSnapshot executeCreateSession(CreateSessionCommand command);
    SessionSnapshot executeJoinSession(JoinSessionCommand command);
    SessionSnapshot executeStartGame(StartGameCommand command);
    SessionSnapshot executePlayCard(PlayCardCommand command);
    SessionSnapshot executePayDebt(PayDebtCommand command);
    SessionSnapshot executeProposeTrade(ProposeTradeCommand command);
    SessionSnapshot executeRespondTrade(RespondTradeCommand command);
    SessionSnapshot executeRespondAction(RespondActionCommand command);
    SessionSnapshot executeDiscardCards(DiscardCardsCommand command);
    SessionSnapshot executeEndTurn(EndTurnCommand command);
    SessionSnapshot executeRejoinSession(RejoinSessionCommand command);
    SessionSnapshot executePlayerDisconnected(PlayerDisconnectedCommand command);
    Map<String, Object> executeProcessDisconnectTimeouts(ProcessDisconnectTimeoutsCommand command);
}
