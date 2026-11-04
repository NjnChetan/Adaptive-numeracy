"""
Knowledge Boundary Algorithm — Graph 1
=========================================
Nodes : T5, T10, 2M1, 2M1C, 3M1, 3M1C, 3M1C2, 2M2, 2M2C, 3M2, 3M2C
Edges : T5->T10, T5->2M1, T10->2M1C, 2M1->2M1C, 2M1->3M1,
        2M1C->3M1C, 3M1->3M1C, 3M1C->3M1C2, 3M1C2->2M2,
        2M2->2M2C, 2M2->3M2, 2M2C->3M2C, 3M2->3M2C

Binary response string: '1' = correct, '0' = wrong.
Each key in DISPATCH is the response string accumulated so far.

Boundary (ZPD) = solvable nodes with at least one edge to an unsolvable node.
  - All nodes solvable    -> boundary = {}
  - No node solvable      -> boundary = {T5}

HOW TO PLUG IN YOUR OWN LOGIC:
  - get_problem(node) calls generate_<node>() (e.g. generate_T5()).
  - Replace check_response(node, problem, student_answer) to return the
    correctness (True/False) of the student's response.
"""

from __future__ import annotations
from dataclasses import dataclass
from typing import Callable

# Replace "questions" with the module that holds your generators.
from questions import (
    generate_T5, generate_T10, generate_2M1, generate_2M1C, generate_3M1,
    generate_3M1C, generate_3M1C2, generate_2M2, generate_2M2C,
    generate_3M2, generate_3M2C,
)


# ---------------------------------------------------------------------------
# Graph definition
# ---------------------------------------------------------------------------

NODES = ['T5', 'T10', '2M1', '2M1C', '3M1', '3M1C', '3M1C2',
         '2M2', '2M2C', '3M2', '3M2C']

EDGES = [
    ('T5',    'T10'),
    ('T5',    '2M1'),
    ('T10',   '2M1C'),
    ('2M1',   '2M1C'),
    ('2M1',   '3M1'),
    ('2M1C',  '3M1C'),
    ('3M1',   '3M1C'),
    ('3M1C',  '3M1C2'),
    ('3M1C2', '2M2'),
    ('2M2',   '2M2C'),
    ('2M2',   '3M2'),
    ('2M2C',  '3M2C'),
    ('3M2',   '3M2C'),
]

GENERATORS: dict[str, Callable[[], object]] = {
    'T5':    generate_T5,
    'T10':   generate_T10,
    '2M1':   generate_2M1,
    '2M1C':  generate_2M1C,
    '3M1':   generate_3M1,
    '3M1C':  generate_3M1C,
    '3M1C2': generate_3M1C2,
    '2M2':   generate_2M2,
    '2M2C':  generate_2M2C,
    '3M2':   generate_3M2,
    '3M2C':  generate_3M2C,
}


# ---------------------------------------------------------------------------
# Hooks — replace check_response with your real implementation
# ---------------------------------------------------------------------------

def get_problem(node: str) -> object:
    """Return a problem instance for the given node type."""
    return GENERATORS[node]()


def check_response(node: str, problem: object, student_answer: object) -> bool:
    """Return True if the student answered correctly."""
    answer = input(f"  Answer for {node} [1=correct, 0=wrong]: ").strip()
    return answer == '1'


# ---------------------------------------------------------------------------
# Core data structures
# ---------------------------------------------------------------------------

@dataclass
class AskState:
    node: str
    def __repr__(self):
        return f"AskState(node={self.node!r})"


@dataclass
class Terminal:
    solvable:   frozenset
    unsolvable: frozenset
    boundary:   frozenset          # the ZPD
    def __repr__(self):
        return (
            f"Terminal(\n"
            f"  solvable   = {_ordered(self.solvable)}\n"
            f"  unsolvable = {_ordered(self.unsolvable)}\n"
            f"  boundary   = {_ordered(self.boundary)}\n"
            f")"
        )


def _ordered(nodes: frozenset) -> list[str]:
    """List nodes in graph order (easiest first) rather than alphabetically."""
    return [n for n in NODES if n in nodes]


# ---------------------------------------------------------------------------
# Dispatch table
# Pre-computed from the greedy decision tree (see algorithm walkthrough below).
# Selection rule: s* = argmax_{s uncolored} min(n+_s, n-_s).
# Ties are broken by lookahead: the tied node giving the fewest total
# questions over all outcomes (average 4.06 questions, optimal for this graph).
#
# DECISION TREE SUMMARY
# ─────────────────────
# Step 0 — Scores across all 11 uncolored nodes:
#   T5(min=1), T10(min=2), 2M1(min=2), 2M1C(min=4), 3M1(min=3),
#   3M1C(min=6)★, 3M1C2(min=5), 2M2(min=4), 2M2C(min=2), 3M2(min=2),
#   3M2C(min=1)
#   Unique maximum at min=6
#   → ASK [3M1C]  (n+=6, n-=6)
#
# ┌─ '1'  3M1C correct → solvable {T5,T10,2M1,2M1C,3M1,3M1C};
# │        remaining {3M1C2,2M2,2M2C,3M2,3M2C}
# │   Scores: 3M1C2(min=1), 2M2(min=2)★, 2M2C(min=2)★, 3M2(min=2)★, 3M2C(min=1)
# │   Tie: {2M2, 2M2C, 3M2} → 2M2C (lookahead)
# │   → ASK [2M2C]  (n+=3, n-=2)
# │
# │  ┌─ '11'  2M2C correct → solvable {3M1C2,2M2,2M2C}; remaining {3M2,3M2C}
# │  │   Scores: 3M2(min=1)★, 3M2C(min=1)★  — tie → 3M2 (lookahead)
# │  │   → ASK [3M2]  (n+=1, n-=2)
# │  │
# │  │  ┌─ '111'  3M2 correct → solvable {3M2}; remaining {3M2C}
# │  │  │   → ASK [3M2C]
# │  │  │   ┌─ '1111'  3M2C correct → ALL SOLVABLE     boundary = {}
# │  │  │   └─ '1110'  3M2C wrong                     boundary = {2M2C, 3M2}
# │  │  │
# │  │  └─ '110'  3M2 wrong → unsolvable {3M2,3M2C}
# │  │      [TERMINAL]                                 boundary = {2M2, 2M2C}
# │  │
# │  └─ '10'  2M2C wrong → unsolvable {2M2C,3M2C}; remaining {3M1C2,2M2,3M2}
# │      Scores: 3M1C2(min=1), 2M2(min=2)★, 3M2(min=1)
# │      → ASK [2M2]  (n+=2, n-=2)
# │      ┌─ '101'  2M2 correct → solvable {3M1C2,2M2}; remaining {3M2}
# │      │   → ASK [3M2]
# │      │   ┌─ '1011'  3M2 correct                    boundary = {2M2, 3M2}
# │      │   └─ '1010'  3M2 wrong                      boundary = {2M2}
# │      │
# │      └─ '100'  2M2 wrong → unsolvable {2M2,3M2}; remaining {3M1C2}
# │          → ASK [3M1C2]
# │          ┌─ '1001'  3M1C2 correct                  boundary = {3M1C2}
# │          └─ '1000'  3M1C2 wrong                    boundary = {3M1C}
# │
# └─ '0'  3M1C wrong → unsolvable {3M1C,3M1C2,2M2,2M2C,3M2,3M2C};
#          remaining {T5,T10,2M1,2M1C,3M1}
#     Scores: T5(min=1), T10(min=2)★, 2M1(min=2)★, 2M1C(min=1), 3M1(min=1)
#     Tie: {T10, 2M1} → T10 (lookahead)
#     → ASK [T10]  (n+=2, n-=2)
#
#     ┌─ '01'  T10 correct → solvable {T5,T10}; remaining {2M1,2M1C,3M1}
#     │   Scores: 2M1(min=1)★, 2M1C(min=1)★, 3M1(min=1)★
#     │   Tie: {2M1, 2M1C, 3M1} → 2M1C (lookahead)
#     │   → ASK [2M1C]  (n+=2, n-=1)
#     │   ┌─ '011'  2M1C correct → solvable {2M1,2M1C}; remaining {3M1}
#     │   │   → ASK [3M1]
#     │   │   ┌─ '0111'  3M1 correct                   boundary = {2M1C, 3M1}
#     │   │   └─ '0110'  3M1 wrong                     boundary = {2M1, 2M1C}
#     │   │
#     │   └─ '010'  2M1C wrong → unsolvable {2M1C}; remaining {2M1,3M1}
#     │       Scores: 2M1(min=1)★, 3M1(min=1)★  — tie → 2M1 (lookahead)
#     │       → ASK [2M1]  (n+=1, n-=2)
#     │       ┌─ '0101'  2M1 correct → solvable {2M1}; remaining {3M1}
#     │       │   → ASK [3M1]
#     │       │   ┌─ '01011'  3M1 correct              boundary = {T10, 2M1, 3M1}
#     │       │   └─ '01010'  3M1 wrong                boundary = {T10, 2M1}
#     │       │
#     │       └─ '0100'  2M1 wrong → unsolvable {2M1,3M1}
#     │           [TERMINAL]                           boundary = {T5, T10}
#     │
#     └─ '00'  T10 wrong → unsolvable {T10,2M1C}; remaining {T5,2M1,3M1}
#         Scores: T5(min=1), 2M1(min=2)★, 3M1(min=1)
#         → ASK [2M1]  (n+=2, n-=2)
#         ┌─ '001'  2M1 correct → solvable {T5,2M1}; remaining {3M1}
#         │   → ASK [3M1]
#         │   ┌─ '0011'  3M1 correct                   boundary = {T5, 2M1, 3M1}
#         │   └─ '0010'  3M1 wrong                     boundary = {T5, 2M1}
#         │
#         └─ '000'  2M1 wrong → unsolvable {2M1,3M1}; remaining {T5}
#             → ASK [T5]
#             ┌─ '0001'  T5 correct                    boundary = {T5}
#             └─ '0000'  T5 wrong → ALL UNSOLVABLE     boundary = {T5}
# ---------------------------------------------------------------------------

DISPATCH: dict[str, AskState | Terminal] = {

    # ── Root ────────────────────────────────────────────────────────────────
    '':      AskState('3M1C'),

    # ── 3M1C correct ────────────────────────────────────────────────────────
    '1':     AskState('2M2C'),

    # ── 3M1C✓  2M2C correct ─────────────────────────────────────────────────
    '11':    AskState('3M2'),

    '111':   AskState('3M2C'),
    '1111':  Terminal(                              # all solvable
        solvable   = frozenset(NODES),
        unsolvable = frozenset(),
        boundary   = frozenset(),
    ),
    '1110':  Terminal(
        solvable   = frozenset(['T5','T10','2M1','2M1C','3M1','3M1C','3M1C2','2M2','2M2C','3M2']),
        unsolvable = frozenset(['3M2C']),
        boundary   = frozenset(['2M2C','3M2']),
    ),

    '110':   Terminal(
        solvable   = frozenset(['T5','T10','2M1','2M1C','3M1','3M1C','3M1C2','2M2','2M2C']),
        unsolvable = frozenset(['3M2','3M2C']),
        boundary   = frozenset(['2M2','2M2C']),
    ),

    # ── 3M1C✓  2M2C wrong ───────────────────────────────────────────────────
    '10':    AskState('2M2'),

    '101':   AskState('3M2'),
    '1011':  Terminal(
        solvable   = frozenset(['T5','T10','2M1','2M1C','3M1','3M1C','3M1C2','2M2','3M2']),
        unsolvable = frozenset(['2M2C','3M2C']),
        boundary   = frozenset(['2M2','3M2']),
    ),
    '1010':  Terminal(
        solvable   = frozenset(['T5','T10','2M1','2M1C','3M1','3M1C','3M1C2','2M2']),
        unsolvable = frozenset(['2M2C','3M2','3M2C']),
        boundary   = frozenset(['2M2']),
    ),

    '100':   AskState('3M1C2'),
    '1001':  Terminal(
        solvable   = frozenset(['T5','T10','2M1','2M1C','3M1','3M1C','3M1C2']),
        unsolvable = frozenset(['2M2','2M2C','3M2','3M2C']),
        boundary   = frozenset(['3M1C2']),
    ),
    '1000':  Terminal(
        solvable   = frozenset(['T5','T10','2M1','2M1C','3M1','3M1C']),
        unsolvable = frozenset(['3M1C2','2M2','2M2C','3M2','3M2C']),
        boundary   = frozenset(['3M1C']),
    ),

    # ── 3M1C wrong ──────────────────────────────────────────────────────────
    '0':     AskState('T10'),

    # ── 3M1C✗  T10 correct ──────────────────────────────────────────────────
    '01':    AskState('2M1C'),

    '011':   AskState('3M1'),
    '0111':  Terminal(
        solvable   = frozenset(['T5','T10','2M1','2M1C','3M1']),
        unsolvable = frozenset(['3M1C','3M1C2','2M2','2M2C','3M2','3M2C']),
        boundary   = frozenset(['2M1C','3M1']),
    ),
    '0110':  Terminal(
        solvable   = frozenset(['T5','T10','2M1','2M1C']),
        unsolvable = frozenset(['3M1','3M1C','3M1C2','2M2','2M2C','3M2','3M2C']),
        boundary   = frozenset(['2M1','2M1C']),
    ),

    '010':   AskState('2M1'),
    '0101':  AskState('3M1'),
    '01011': Terminal(
        solvable   = frozenset(['T5','T10','2M1','3M1']),
        unsolvable = frozenset(['2M1C','3M1C','3M1C2','2M2','2M2C','3M2','3M2C']),
        boundary   = frozenset(['T10','2M1','3M1']),
    ),
    '01010': Terminal(
        solvable   = frozenset(['T5','T10','2M1']),
        unsolvable = frozenset(['2M1C','3M1','3M1C','3M1C2','2M2','2M2C','3M2','3M2C']),
        boundary   = frozenset(['T10','2M1']),
    ),
    '0100':  Terminal(
        solvable   = frozenset(['T5','T10']),
        unsolvable = frozenset(['2M1','2M1C','3M1','3M1C','3M1C2','2M2','2M2C','3M2','3M2C']),
        boundary   = frozenset(['T5','T10']),
    ),

    # ── 3M1C✗  T10 wrong ────────────────────────────────────────────────────
    '00':    AskState('2M1'),

    '001':   AskState('3M1'),
    '0011':  Terminal(
        solvable   = frozenset(['T5','2M1','3M1']),
        unsolvable = frozenset(['T10','2M1C','3M1C','3M1C2','2M2','2M2C','3M2','3M2C']),
        boundary   = frozenset(['T5','2M1','3M1']),
    ),
    '0010':  Terminal(
        solvable   = frozenset(['T5','2M1']),
        unsolvable = frozenset(['T10','2M1C','3M1','3M1C','3M1C2','2M2','2M2C','3M2','3M2C']),
        boundary   = frozenset(['T5','2M1']),
    ),

    '000':   AskState('T5'),
    '0001':  Terminal(
        solvable   = frozenset(['T5']),
        unsolvable = frozenset(['T10','2M1','2M1C','3M1','3M1C','3M1C2','2M2','2M2C','3M2','3M2C']),
        boundary   = frozenset(['T5']),
    ),
    '0000':  Terminal(                              # all unsolvable
        solvable   = frozenset(),
        unsolvable = frozenset(NODES),
        boundary   = frozenset(['T5']),
    ),
}


# ---------------------------------------------------------------------------
# Runtime driver
# ---------------------------------------------------------------------------

def run_assessment(
    get_problem_fn:    Callable[[str], object]               = get_problem,
    check_response_fn: Callable[[str, object, object], bool] = check_response,
    verbose: bool = True,
) -> Terminal:
    """
    Run the adaptive knowledge-boundary assessment.

    Parameters
    ----------
    get_problem_fn    : callable(node) -> problem
    check_response_fn : callable(node, problem, answer) -> bool
    verbose           : print progress

    Returns
    -------
    Terminal  with .solvable, .unsolvable, .boundary (the ZPD)
    """
    response_string = ''

    if verbose:
        print("=" * 55)
        print("  Knowledge Boundary Assessment  (Graph 1)")
        print("=" * 55)

    while True:
        state = DISPATCH.get(response_string)

        if state is None:
            raise KeyError(f"Unknown response string {response_string!r}")

        if isinstance(state, Terminal):
            if verbose:
                print("\n── Assessment complete ──────────────────────────────")
                print(f"  Questions asked : {len(response_string)}")
                print(f"  Solvable        : {_ordered(state.solvable)}")
                print(f"  Unsolvable      : {_ordered(state.unsolvable)}")
                print(f"  Boundary (ZPD)  : {_ordered(state.boundary)}")
            return state

        node    = state.node
        problem = get_problem_fn(node)

        if verbose:
            print(f"\n[Q{len(response_string)+1}] Node type : {node}")
            print(f"  Problem  : {problem}")

        correct = check_response_fn(node, problem, None)
        response_string += '1' if correct else '0'

        if verbose:
            print(f"  Response : {'✓ Correct' if correct else '✗ Wrong'}"
                  f"  (string: {response_string!r})")


# ---------------------------------------------------------------------------
# Convenience lookup
# ---------------------------------------------------------------------------

def lookup(response_string: str) -> AskState | Terminal:
    """Return the state for any binary response string."""
    state = DISPATCH.get(response_string)
    if state is None:
        raise KeyError(f"No entry for {response_string!r}")
    return state


# ---------------------------------------------------------------------------
# Entrypoint
# ---------------------------------------------------------------------------

if __name__ == '__main__':
    run_assessment()
