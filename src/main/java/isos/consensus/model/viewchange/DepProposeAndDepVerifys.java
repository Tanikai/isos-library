package isos.consensus.model.viewchange;

import isos.message.replica.fast.DepProposeWithRequest;
import isos.message.replica.fast.DepVerifyMessage;

import java.util.List;

public record DepProposeAndDepVerifys(
        DepProposeWithRequest depPropose,
        List<DepVerifyMessage> depVerifys
) {}
