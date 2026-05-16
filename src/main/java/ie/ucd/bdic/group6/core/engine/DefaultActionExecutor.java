package ie.ucd.bdic.group6.core.engine;

import ie.ucd.bdic.group6.command.*;
import ie.ucd.bdic.group6.exception.GameException;

import java.util.Map;

/**
 * 默认空实现，用于编译通过和开发阶段占位
 * 等Yiming Liu完成真正的GameEngine实现后，替换此类即可
 */
public class DefaultActionExecutor implements ActionExecutor {
    @Override
    public Map<String, Object> executeCreateSession(CreateSessionCommand command) {
        throw new GameException("NOT_IMPLEMENTED", "CreateSession is not implemented yet");
    }

    @Override
    public Map<String, Object> executeJoinSession(JoinSessionCommand command) {
        throw new GameException("NOT_IMPLEMENTED", "JoinSession is not implemented yet");
    }

    @Override
    public Map<String, Object> executeStartGame(StartGameCommand command) {
        throw new GameException("NOT_IMPLEMENTED", "StartGame is not implemented yet");
    }

    @Override
    public Map<String, Object> executePlayCard(PlayCardCommand command) {
        throw new GameException("NOT_IMPLEMENTED", "PlayCard is not implemented yet");
    }

    @Override
    public Map<String, Object> executePayDebt(PayDebtCommand command) {
        throw new GameException("NOT_IMPLEMENTED", "PayDebt is not implemented yet");
    }

    @Override
    public Map<String, Object> executeProposeTrade(ProposeTradeCommand command) {
        throw new GameException("NOT_IMPLEMENTED", "ProposeTrade is not implemented yet");
    }

    @Override
    public Map<String, Object> executeRespondTrade(RespondTradeCommand command) {
        throw new GameException("NOT_IMPLEMENTED", "RespondTrade is not implemented yet");
    }

    @Override
    public Map<String, Object> executeDiscardCards(DiscardCardsCommand command) {
        throw new GameException("NOT_IMPLEMENTED", "DiscardCards is not implemented yet");
    }

    @Override
    public Map<String, Object> executeEndTurn(EndTurnCommand command) {
        throw new GameException("NOT_IMPLEMENTED", "EndTurn is not implemented yet");
    }

    @Override
    public Map<String, Object> executeRejoinSession(RejoinSessionCommand command) {
        throw new GameException("NOT_IMPLEMENTED", "RejoinSession is not implemented yet");
    }
}