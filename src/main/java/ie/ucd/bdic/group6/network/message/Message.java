package ie.ucd.bdic.group6.network.message;

import java.io.Serializable;

public sealed interface Message extends Serializable
        permits ClientRequest, ServerResponse, ServerUpdate, ServerNotice {}