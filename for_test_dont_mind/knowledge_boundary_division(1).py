"""
Knowledge Boundary Algorithm — Graph 2
=========================================
Nodes : 2D1, 2D1R, 3D1, 3D2, 3D1R, 3D2R, 3D1Z, 4D1R, 4D2R
Edges : 2D1->2D1R, 2D1->3D1, 2D1R->3D1R, 3D1->3D1R, 3D1->3D2,
        3D2->3D2R, 3D1R->3D1Z, 3D1R->4D1R, 3D1R->3D2R,
        4D1R->4D2R, 3D2R->4D2R

Binary response string: '1' = correct, '0' = wrong.
Each key in DISPATCH is the response string accumulated so far.

Boundary (ZPD) = solvable nodes with at least one edge to an unsolvable node.
  - All nodes solvable    -> boundary = {}
  - No node solvable      -> boundary = {2D1}

HOW TO PLUG IN YOUR OWN LOGIC:
  - get_problem(node) calls generate_<node>() (e.g. generate_2D1()).
  - Replace check_response(node, problem, student_answer) to return the
    correctness (True/False) of the student's response.
"""

from __future__ import annotations
from dataclasses import dataclass
from typing import Callable

# Replace "questions" with the module that holds your generators.
from questions import (
    generate_2D1, generate_2D1R, generate_3D1, generate_3D2, generate_3D1R,
    generate_3D2R, generate_3D1Z, generate_4D1R, generate_4D2R,
)


# ---------------------------------------------------------------------------
# Graph definition
# ---------------------------------------------------------------------------

NODES = ['2D1', '2D1R', '3D1', '3D2', '3D1R', '3D2R', '3D1Z', '4D1R', '4D2R']

EDGES = [
    ('2D1',  '2D1R'),
    ('2D1',  '3D1'),
    ('2D1R', '3D1R'),
    ('3D1',  '3D1R'),
    ('3D1',  '3D2'),
    ('3D2',  '3D2R'),
    ('3D1R', '3D1Z'),
    ('3D1R', '4D1R'),
    ('3D1R', '3D2R'),
    ('4D1R', '4D2R'),
    ('3D2R', '4D2R'),
]

GENERATORS: dict[str, Callable[[], object]] = {
    '2D1':  generate_2D1,
    '2D1R': generate_2D1R,
    '3D1':  generate_3D1,
    '3D2':  generate_3D2,
    '3D1R': generate_3D1R,
    '3D2R': generate_3D2R,
    '3D1Z': generate_3D1Z,
    '4D1R': generate_4D1R,
    '4D2R': generate_4D2R,
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
# questions over all outcomes (average 4.52 questions). On this graph every
# tied option gives the same total, so lookahead and node order produce the
# same tree.
#
# DECISION TREE SUMMARY
# ─────────────────────
# Step 0 — Scores across all 9 uncolored nodes:
#   2D1(min=1), 2D1R(min=2), 3D1(min=2), 3D2(min=3), 3D1R(min=4)★,
#   3D2R(min=2), 3D1Z(min=1), 4D1R(min=2), 4D2R(min=1)
#   Unique maximum at min=4
#   → ASK [3D1R]  (n+=4, n-=5)
#
# ┌─ '1'  3D1R correct → solvable {2D1,2D1R,3D1,3D1R};
# │        remaining {3D2,3D2R,3D1Z,4D1R,4D2R}
# │   Scores: 3D2(min=1), 3D2R(min=2)★, 3D1Z(min=1), 4D1R(min=1), 4D2R(min=1)
# │   → ASK [3D2R]  (n+=2, n-=2)
# │   ┌─ '11'  3D2R correct → solvable {3D2,3D2R}; remaining {3D1Z,4D1R,4D2R}
# │   │   Scores: 3D1Z(min=1)★, 4D1R(min=1)★, 4D2R(min=1)★
# │   │   Tie: {3D1Z, 4D1R, 4D2R} → 3D1Z (lookahead; tied options cost the same)
# │   │   → ASK [3D1Z]  (n+=1, n-=1)
# │   │   ┌─ '111'  3D1Z correct → solvable {3D1Z}; remaining {4D1R,4D2R}
# │   │   │   Scores: 4D1R(min=1)★, 4D2R(min=1)★
# │   │   │   Tie: {4D1R, 4D2R} → 4D1R (lookahead; tied options cost the same)
# │   │   │   → ASK [4D1R]  (n+=1, n-=2)
# │   │   │   ┌─ '1111'  4D1R correct → solvable {4D1R}; remaining {4D2R}
# │   │   │   │   → ASK [4D2R]
# │   │   │   │   ┌─ '11111'  4D2R correct → ALL SOLVABLE     boundary = {}
# │   │   │   │   └─ '11110'  4D2R wrong                      boundary = {3D2R, 4D1R}
# │   │   │   │
# │   │   │   └─ '1110'  4D1R wrong                           boundary = {3D1R, 3D2R}
# │   │   │
# │   │   └─ '110'  3D1Z wrong → unsolvable {3D1Z}; remaining {4D1R,4D2R}
# │   │       Scores: 4D1R(min=1)★, 4D2R(min=1)★
# │   │       Tie: {4D1R, 4D2R} → 4D1R (lookahead; tied options cost the same)
# │   │       → ASK [4D1R]  (n+=1, n-=2)
# │   │       ┌─ '1101'  4D1R correct → solvable {4D1R}; remaining {4D2R}
# │   │       │   → ASK [4D2R]
# │   │       │   ┌─ '11011'  4D2R correct                    boundary = {3D1R}
# │   │       │   └─ '11010'  4D2R wrong                      boundary = {3D1R, 3D2R, 4D1R}
# │   │       │
# │   │       └─ '1100'  4D1R wrong                           boundary = {3D1R, 3D2R}
# │   │
# │   └─ '10'  3D2R wrong → unsolvable {3D2R,4D2R}; remaining {3D2,3D1Z,4D1R}
# │       Scores: 3D2(min=1)★, 3D1Z(min=1)★, 4D1R(min=1)★
# │       Tie: {3D2, 3D1Z, 4D1R} → 3D2 (lookahead; tied options cost the same)
# │       → ASK [3D2]  (n+=1, n-=1)
# │       ┌─ '101'  3D2 correct → solvable {3D2}; remaining {3D1Z,4D1R}
# │       │   Scores: 3D1Z(min=1)★, 4D1R(min=1)★
# │       │   Tie: {3D1Z, 4D1R} → 3D1Z (lookahead; tied options cost the same)
# │       │   → ASK [3D1Z]  (n+=1, n-=1)
# │       │   ┌─ '1011'  3D1Z correct → solvable {3D1Z}; remaining {4D1R}
# │       │   │   → ASK [4D1R]
# │       │   │   ┌─ '10111'  4D1R correct                    boundary = {3D2, 3D1R, 4D1R}
# │       │   │   └─ '10110'  4D1R wrong                      boundary = {3D2, 3D1R}
# │       │   │
# │       │   └─ '1010'  3D1Z wrong → unsolvable {3D1Z}; remaining {4D1R}
# │       │       → ASK [4D1R]
# │       │       ┌─ '10101'  4D1R correct                    boundary = {3D2, 3D1R, 4D1R}
# │       │       └─ '10100'  4D1R wrong                      boundary = {3D2, 3D1R}
# │       │
# │       └─ '100'  3D2 wrong → unsolvable {3D2}; remaining {3D1Z,4D1R}
# │           Scores: 3D1Z(min=1)★, 4D1R(min=1)★
# │           Tie: {3D1Z, 4D1R} → 3D1Z (lookahead; tied options cost the same)
# │           → ASK [3D1Z]  (n+=1, n-=1)
# │           ┌─ '1001'  3D1Z correct → solvable {3D1Z}; remaining {4D1R}
# │           │   → ASK [4D1R]
# │           │   ┌─ '10011'  4D1R correct                    boundary = {3D1, 3D1R, 4D1R}
# │           │   └─ '10010'  4D1R wrong                      boundary = {3D1, 3D1R}
# │           │
# │           └─ '1000'  3D1Z wrong → unsolvable {3D1Z}; remaining {4D1R}
# │               → ASK [4D1R]
# │               ┌─ '10001'  4D1R correct                    boundary = {3D1, 3D1R, 4D1R}
# │               └─ '10000'  4D1R wrong                      boundary = {3D1, 3D1R}
# │
# └─ '0'  3D1R wrong → unsolvable {3D1R,3D2R,3D1Z,4D1R,4D2R};
#          remaining {2D1,2D1R,3D1,3D2}
#     Scores: 2D1(min=1), 2D1R(min=1), 3D1(min=2)★, 3D2(min=1)
#     → ASK [3D1]  (n+=2, n-=2)
#     ┌─ '01'  3D1 correct → solvable {2D1,3D1}; remaining {2D1R,3D2}
#     │   Scores: 2D1R(min=1)★, 3D2(min=1)★
#     │   Tie: {2D1R, 3D2} → 2D1R (lookahead; tied options cost the same)
#     │   → ASK [2D1R]  (n+=1, n-=1)
#     │   ┌─ '011'  2D1R correct → solvable {2D1R}; remaining {3D2}
#     │   │   → ASK [3D2]
#     │   │   ┌─ '0111'  3D2 correct                          boundary = {2D1R, 3D1, 3D2}
#     │   │   └─ '0110'  3D2 wrong                            boundary = {2D1R, 3D1}
#     │   │
#     │   └─ '010'  2D1R wrong → unsolvable {2D1R}; remaining {3D2}
#     │       → ASK [3D2]
#     │       ┌─ '0101'  3D2 correct                          boundary = {2D1, 3D1, 3D2}
#     │       └─ '0100'  3D2 wrong                            boundary = {2D1, 3D1}
#     │
#     └─ '00'  3D1 wrong → unsolvable {3D1,3D2}; remaining {2D1,2D1R}
#         Scores: 2D1(min=1)★, 2D1R(min=1)★
#         Tie: {2D1, 2D1R} → 2D1 (lookahead; tied options cost the same)
#         → ASK [2D1]  (n+=1, n-=2)
#         ┌─ '001'  2D1 correct → solvable {2D1}; remaining {2D1R}
#         │   → ASK [2D1R]
#         │   ┌─ '0011'  2D1R correct                         boundary = {2D1, 2D1R}
#         │   └─ '0010'  2D1R wrong                           boundary = {2D1}
#         │
#         └─ '000'  2D1 wrong → ALL UNSOLVABLE                boundary = {2D1}
# ---------------------------------------------------------------------------

DISPATCH: dict[str, AskState | Terminal] = {

    # ── Root ─────────────────────────────────────────────────────────────────
    '':      AskState('3D1R'),

    # ── 3D1R correct ─────────────────────────────────────────────────────────
    '1':     AskState('3D2R'),

    # ── 3D1R✓  3D2R correct ──────────────────────────────────────────────────
    '11':    AskState('3D1Z'),
    '111':   AskState('4D1R'),
    '1111':  AskState('4D2R'),
    '11111': Terminal(                              # all solvable
        solvable   = frozenset(NODES),
        unsolvable = frozenset(),
        boundary   = frozenset(),
    ),
    '11110': Terminal(
        solvable   = frozenset(['2D1','2D1R','3D1','3D2','3D1R','3D2R','3D1Z','4D1R']),
        unsolvable = frozenset(['4D2R']),
        boundary   = frozenset(['3D2R','4D1R']),
    ),
    '1110':  Terminal(
        solvable   = frozenset(['2D1','2D1R','3D1','3D2','3D1R','3D2R','3D1Z']),
        unsolvable = frozenset(['4D1R','4D2R']),
        boundary   = frozenset(['3D1R','3D2R']),
    ),
    '110':   AskState('4D1R'),
    '1101':  AskState('4D2R'),
    '11011': Terminal(
        solvable   = frozenset(['2D1','2D1R','3D1','3D2','3D1R','3D2R','4D1R','4D2R']),
        unsolvable = frozenset(['3D1Z']),
        boundary   = frozenset(['3D1R']),
    ),
    '11010': Terminal(
        solvable   = frozenset(['2D1','2D1R','3D1','3D2','3D1R','3D2R','4D1R']),
        unsolvable = frozenset(['3D1Z','4D2R']),
        boundary   = frozenset(['3D1R','3D2R','4D1R']),
    ),
    '1100':  Terminal(
        solvable   = frozenset(['2D1','2D1R','3D1','3D2','3D1R','3D2R']),
        unsolvable = frozenset(['3D1Z','4D1R','4D2R']),
        boundary   = frozenset(['3D1R','3D2R']),
    ),

    # ── 3D1R✓  3D2R wrong ────────────────────────────────────────────────────
    '10':    AskState('3D2'),
    '101':   AskState('3D1Z'),
    '1011':  AskState('4D1R'),
    '10111': Terminal(
        solvable   = frozenset(['2D1','2D1R','3D1','3D2','3D1R','3D1Z','4D1R']),
        unsolvable = frozenset(['3D2R','4D2R']),
        boundary   = frozenset(['3D2','3D1R','4D1R']),
    ),
    '10110': Terminal(
        solvable   = frozenset(['2D1','2D1R','3D1','3D2','3D1R','3D1Z']),
        unsolvable = frozenset(['3D2R','4D1R','4D2R']),
        boundary   = frozenset(['3D2','3D1R']),
    ),
    '1010':  AskState('4D1R'),
    '10101': Terminal(
        solvable   = frozenset(['2D1','2D1R','3D1','3D2','3D1R','4D1R']),
        unsolvable = frozenset(['3D2R','3D1Z','4D2R']),
        boundary   = frozenset(['3D2','3D1R','4D1R']),
    ),
    '10100': Terminal(
        solvable   = frozenset(['2D1','2D1R','3D1','3D2','3D1R']),
        unsolvable = frozenset(['3D2R','3D1Z','4D1R','4D2R']),
        boundary   = frozenset(['3D2','3D1R']),
    ),
    '100':   AskState('3D1Z'),
    '1001':  AskState('4D1R'),
    '10011': Terminal(
        solvable   = frozenset(['2D1','2D1R','3D1','3D1R','3D1Z','4D1R']),
        unsolvable = frozenset(['3D2','3D2R','4D2R']),
        boundary   = frozenset(['3D1','3D1R','4D1R']),
    ),
    '10010': Terminal(
        solvable   = frozenset(['2D1','2D1R','3D1','3D1R','3D1Z']),
        unsolvable = frozenset(['3D2','3D2R','4D1R','4D2R']),
        boundary   = frozenset(['3D1','3D1R']),
    ),
    '1000':  AskState('4D1R'),
    '10001': Terminal(
        solvable   = frozenset(['2D1','2D1R','3D1','3D1R','4D1R']),
        unsolvable = frozenset(['3D2','3D2R','3D1Z','4D2R']),
        boundary   = frozenset(['3D1','3D1R','4D1R']),
    ),
    '10000': Terminal(
        solvable   = frozenset(['2D1','2D1R','3D1','3D1R']),
        unsolvable = frozenset(['3D2','3D2R','3D1Z','4D1R','4D2R']),
        boundary   = frozenset(['3D1','3D1R']),
    ),

    # ── 3D1R wrong ───────────────────────────────────────────────────────────
    '0':     AskState('3D1'),

    # ── 3D1R✗  3D1 correct ───────────────────────────────────────────────────
    '01':    AskState('2D1R'),
    '011':   AskState('3D2'),
    '0111':  Terminal(
        solvable   = frozenset(['2D1','2D1R','3D1','3D2']),
        unsolvable = frozenset(['3D1R','3D2R','3D1Z','4D1R','4D2R']),
        boundary   = frozenset(['2D1R','3D1','3D2']),
    ),
    '0110':  Terminal(
        solvable   = frozenset(['2D1','2D1R','3D1']),
        unsolvable = frozenset(['3D2','3D1R','3D2R','3D1Z','4D1R','4D2R']),
        boundary   = frozenset(['2D1R','3D1']),
    ),
    '010':   AskState('3D2'),
    '0101':  Terminal(
        solvable   = frozenset(['2D1','3D1','3D2']),
        unsolvable = frozenset(['2D1R','3D1R','3D2R','3D1Z','4D1R','4D2R']),
        boundary   = frozenset(['2D1','3D1','3D2']),
    ),
    '0100':  Terminal(
        solvable   = frozenset(['2D1','3D1']),
        unsolvable = frozenset(['2D1R','3D2','3D1R','3D2R','3D1Z','4D1R','4D2R']),
        boundary   = frozenset(['2D1','3D1']),
    ),

    # ── 3D1R✗  3D1 wrong ─────────────────────────────────────────────────────
    '00':    AskState('2D1'),
    '001':   AskState('2D1R'),
    '0011':  Terminal(
        solvable   = frozenset(['2D1','2D1R']),
        unsolvable = frozenset(['3D1','3D2','3D1R','3D2R','3D1Z','4D1R','4D2R']),
        boundary   = frozenset(['2D1','2D1R']),
    ),
    '0010':  Terminal(
        solvable   = frozenset(['2D1']),
        unsolvable = frozenset(['2D1R','3D1','3D2','3D1R','3D2R','3D1Z','4D1R','4D2R']),
        boundary   = frozenset(['2D1']),
    ),
    '000':   Terminal(                              # all unsolvable
        solvable   = frozenset(),
        unsolvable = frozenset(NODES),
        boundary   = frozenset(['2D1']),
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
        print("  Knowledge Boundary Assessment  (Graph 2)")
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
