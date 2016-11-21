/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.elasticsearch.cluster.routing.allocation;

import org.elasticsearch.cluster.routing.allocation.decider.Decision;
import org.elasticsearch.common.Nullable;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.xcontent.XContentBuilder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents a decision to move a started shard to form a more optimally balanced cluster.
 */
public final class RebalanceDecision extends RelocationDecision {
    /** a constant representing no decision taken */
    public static final RebalanceDecision NOT_TAKEN = new RebalanceDecision(null, null, null, null, Float.POSITIVE_INFINITY, null);

    @Nullable
    private final Decision canRebalanceDecision;
    @Nullable
    private final String explanation;
    @Nullable
    private final Map<String, NodeRebalanceResult> nodeDecisions;
    private float currentWeight;

    private RebalanceDecision(Decision canRebalanceDecision, Decision.Type finalDecision, String assignedNodeId,
                              Map<String, NodeRebalanceResult> nodeDecisions, float currentWeight, String explanation) {
        super(finalDecision, assignedNodeId);
        this.canRebalanceDecision = canRebalanceDecision;
        this.nodeDecisions = nodeDecisions != null ? Collections.unmodifiableMap(nodeDecisions) : null;
        this.currentWeight = currentWeight;
        this.explanation = explanation;
    }

    public RebalanceDecision(StreamInput in) throws IOException {
        super(in);
        if (in.readBoolean()) {
            canRebalanceDecision = Decision.readFrom(in);
        } else {
            canRebalanceDecision = null;
        }
        Map<String, NodeRebalanceResult> nodeDecisionsMap = null;
        if (in.readBoolean()) {
            final int size = in.readVInt();
            nodeDecisionsMap = new HashMap<>(size);
            for (int i = 0; i < size; i++) {
                nodeDecisionsMap.put(in.readString(), new NodeRebalanceResult(in));
            }
        }
        nodeDecisions = nodeDecisionsMap == null ? null : Collections.unmodifiableMap(nodeDecisionsMap);
        currentWeight = in.readFloat();
        explanation = in.readOptionalString();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        if (canRebalanceDecision != null) {
            out.writeBoolean(true);
            Decision.writeTo(canRebalanceDecision, out);
        } else {
            out.writeBoolean(false);
        }
        if (nodeDecisions != null) {
            out.writeBoolean(true);
            out.writeVInt(nodeDecisions.size());
            for (Map.Entry<String, NodeRebalanceResult> entry : nodeDecisions.entrySet()) {
                out.writeString(entry.getKey());
                entry.getValue().writeTo(out);
            }
        } else {
            out.writeBoolean(false);
        }
        out.writeFloat(currentWeight);
        out.writeOptionalString(explanation);
    }

    /**
     * Creates a new NO {@link RebalanceDecision}.
     */
    public static RebalanceDecision no(Decision canRebalanceDecision, Map<String, NodeRebalanceResult> nodeDecisions,
                                       float currentWeight, String explanation) {
        return new RebalanceDecision(canRebalanceDecision, Decision.Type.NO, null, nodeDecisions, currentWeight, explanation);
    }

    /**
     * Creates a new {@link RebalanceDecision}.
     */
    public static RebalanceDecision decision(Decision canRebalanceDecision, Decision.Type finalDecision, String assignedNodeId,
                                             Map<String, NodeRebalanceResult> nodeDecisions, float currentWeight, String explanation) {
        return new RebalanceDecision(canRebalanceDecision, finalDecision, assignedNodeId, nodeDecisions, currentWeight, explanation);
    }

    /**
     * Returns the decision for being allowed to rebalance the shard.
     */
    @Nullable
    public Decision getCanRebalanceDecision() {
        return canRebalanceDecision;
    }

    /**
     * Gets the individual node-level decisions that went into making the final decision as represented by
     * {@link #getFinalDecisionType()}.  The map that is returned has the node id as the key and a {@link NodeRebalanceResult}.
     */
    @Nullable
    public Map<String, NodeRebalanceResult> getNodeDecisions() {
        return nodeDecisions;
    }

    @Override
    public String getFinalExplanation() {
        return explanation;
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        super.toXContent(builder, params);
        builder.field("current_weight", currentWeight);
        builder.startObject("can_rebalance_decision");
        {
            builder.field("final_decision", canRebalanceDecision.type().toString());
            canRebalanceDecision.toXContent(builder, params);
        }
        builder.endObject();
        if (nodeDecisions != null) {
            builder.startObject("nodes");
            {
                List<String> nodeIds = new ArrayList<>(nodeDecisions.keySet());
                Collections.sort(nodeIds);
                for (String nodeId : nodeIds) {
                    NodeRebalanceResult result = nodeDecisions.get(nodeId);
                    result.toXContent(builder, params);
                }
            }
            builder.endObject();
        }
        return builder;
    }
}
