Follow the standard skill template: Goal, Use When, Do Not Use, Steps, Output.

Create the promoted skill at `/home/err/spaces/Truth/.agents/skills/<name>/SKILL.md` with YAML frontmatter containing `name`, `description`, `license: GPL-3.0-or-later`, `compatibility: opencode`, and `metadata` listing `audience`, `workflow`, `project: gates-of-truth`, `discoverable-by: [opencode, eta-mu, claude]`, and `version: 1`.

Also create `/home/err/spaces/Truth/.agents/skills/<name>/CONTRACT.edn` as a minimal eta-mu skill contract with `name`, `v`, `intent`, `activation` (priority, triggers), `governance`, `effects`, and `protocol/workflow`.

After promoting, ensure the skill is listed in `AGENTS.md` under the Agent Skills section and mentioned in `CLAUDE.md` under the project-local skills section so Claude and human reviewers can find it.
