# Stellar Mergers, Accretion, and Mass Transfer

**Topic:** How stars grow through mergers, shell feeding, and binary mass transfer.  
**Source:** `modeling stellar merges and feeding` conversation chunks (7 files).  
**Status:** Bondi/HLL/Roche-lobe overflow partly covered in `docs/research/physics/rate-limited-accretion-mass-transfer.md`; merger regimes and common-envelope evolution remain open.

## Two Regimes

1. **Star-star coalescence** — Combining two stellar structures into one remnant.
2. **Shell feeding inside one massive star** — Convection and nuclear burning let one shell entrain material from another.

## Star-Star Mergers

A practical reduced model is **entropy sorting plus a shock-heating correction**, calibrated against 3D SPH or MHD merger simulations. Recent comparisons (e.g., PyMMAMS-style methods) find that plain entropy sorting overmixes hydrogen into the core and makes the remnant look too rejuvenated; adding shock heating improves post-merger thermal and composition structure.

Key variables:
- Entropy profile
- Composition by layer
- RMS convective velocity and turnover time
- Boundary migration
- Nuclear energy generation before and after contact

## Shell Feeding

3D simulations of late-stage massive stars show that shell mergers are driven by entrainment and erosion of stable layers, not smooth diffusion. Convective velocities are much faster than 1D mixing-length theory predicts, and the merged shell can host multiple burning phases with an asymmetric chemical structure.

## Binary Mass Transfer (Roche-Lobe Overflow)

When the donor star expands to fill its Roche lobe, gas streams through the inner Lagrange point L1 toward the accretor. Whether the stream forms an accretion disk or impacts directly depends on geometry and accretor type.

Key controls:
- Donor mass and radius
- Accretor mass and radius
- Orbital separation/period
- Roche-lobe geometry and L1 nozzle
- Angular momentum loss prescription
- Direct impact vs. disk formation rule

Possible outcomes:
- Steady transfer
- Runaway transfer
- Common-envelope evolution
- Accretor-dependent physics: normal-star rejuvenation, white-dwarf novae/SNe Ia, neutron-star/black-hole X-ray binaries

## Truth Relevance

- **Phase 0:** Mass transfer provides a second channel for star growth besides gas accretion.
- **Habitability:** Binary stars create different calendars, eclipses, tides, and sky-culture compared to single-star systems.
- **Architecture:** Roche-lobe overflow must be an influence in the ECS registry, emitted on both donor and accretor.

## Gaps vs. Existing Research

`docs/research/physics/rate-limited-accretion-mass-transfer.md` covers Bondi-Hoyle-Lyttleton sink accretion, Roche-lobe overflow, and sink-particle gradual debit. Missing from that notebook:
- Stellar merger regime classification (coalescence vs. grazing collision vs. common envelope)
- Shock-heating correction for entropy sorting
- Shell-merger / entrainment physics inside evolved stars
- Stellar-wind accretion as a weaker alternative to Roche-lobe overflow

## Connections to Other Topics

- `ecs-physics-substrate` provides the influence registry for donor→accretor mass transfer.
- `phase0-nebula` needs to account for binary outcomes and close-multiple formation.
- `hops315-fsm` defines the `:Role` FSM that classifies objects as stars, brown dwarfs, planets, etc.
- `deep-research-brief` Section 7 (galaxy context) includes binary star populations and IMF effects.

## Open Questions

- What is the minimum merger remnant model for Phase 0 resolution?
- Should common-envelope evolution be resolved or treated as a statistical event?
- How do we represent the L1 stream and accretion disk without a second hydrodynamic solver?
- What is the critical mass ratio that separates stable from unstable Roche-lobe transfer?
