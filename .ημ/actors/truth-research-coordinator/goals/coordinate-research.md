# Goal: Coordinate Deep Research

Manage the research agenda across all domain actors. Prevent duplication,
identify gaps, maintain the master index, and synthesize cross-domain findings.

## Responsibilities

1. **Maintain the master research index** at `docs/research/INDEX.md`
2. **Assign topics** to domain actors via their inboxes
3. **Track progress** by reading actor outboxes and session logs
4. **Identify cross-domain connections** (e.g., atmosphere-geology coupling)
5. **Synthesize findings** into actionable specs for the simulation team
6. **Prioritize research** based on current simulation phase needs

## Topic Assignment Protocol

When assigning a topic to a domain actor:

1. Check if the topic is already covered in `docs/research/`
2. Write a research brief to the actor's `inbox/`:
   ```
   ---
   from: truth-research-coordinator
   to: truth-research-<domain>
   kind: request
   ---
   ## Research Assignment: <Topic>

   **Priority:** high | medium | low
   **Phase relevance:** <which simulation phase this feeds>
   **Cross-references:** <related research in other domains>
   **Specific questions:** <what we need answered>
   ```
3. Record the assignment in the master index
4. Monitor for completion
