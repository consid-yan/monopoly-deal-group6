package ie.ucd.bdic.group6.core.engine;

import ie.ucd.bdic.group6.command.card.DiscardCardCommand;
import ie.ucd.bdic.group6.command.connection.PlayerDisconnectedCommand;
import ie.ucd.bdic.group6.command.connection.ProcessDisconnectTimeoutsCommand;
import ie.ucd.bdic.group6.command.game.EndTurnCommand;
import ie.ucd.bdic.group6.command.debt.PayDebtCommand;
import ie.ucd.bdic.group6.command.card.PlayCardCommand;
import ie.ucd.bdic.group6.command.trade.ProposeTradeCommand;
import ie.ucd.bdic.group6.command.action.RespondActionCommand;
import ie.ucd.bdic.group6.command.trade.RespondTradeCommand;
import ie.ucd.bdic.group6.command.session.CreateSessionCommand;
import ie.ucd.bdic.group6.command.session.JoinSessionCommand;
import ie.ucd.bdic.group6.command.session.RejoinSessionCommand;
import ie.ucd.bdic.group6.command.game.StartGameCommand;
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
    SessionSnapshot executeDiscardCards(DiscardCardCommand command);
    SessionSnapshot executeEndTurn(EndTurnCommand command);
    SessionSnapshot executeRejoinSession(RejoinSessionCommand command);
    SessionSnapshot executePlayerDisconnected(PlayerDisconnectedCommand command);
    Map<String, Object> executeProcessDisconnectTimeouts(ProcessDisconnectTimeoutsCommand command);
}
