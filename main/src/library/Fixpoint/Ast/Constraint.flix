/*
 * Copyright 2021 Benjamin Dahse
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

namespace Fixpoint/Ast {

    use Fixpoint.PredSymsOf
    use Fixpoint/PredSymsOf.predSymsOf
    use Fixpoint.SubstitutePredSym
    use Fixpoint/SubstitutePredSym.substitute
    use Fixpoint/Shared.PredSym

    @Internal
    pub enum Constraint[v] {
        case Constraint(HeadPredicate[v], List[BodyPredicate[v]])
    }

    instance PredSymsOf[Constraint[v]] {
        pub def predSymsOf(x: Constraint[v]): Set[PredSym] = match x {
            case Constraint(head, body) =>
                let headSyms = predSymsOf(head);
                let bodySyms = List.map(predSymsOf, body);
                headSyms ++ Monoid.fold(bodySyms)
        }
    }

    instance SubstitutePredSym[Constraint[v]] {
        pub def substitute(x: Constraint[v], s: Map[PredSym, PredSym]): Constraint[v] = match x {
            case Constraint(head, body) =>
                let newHead = substitute(head, s);
                let newBody = List.map(p -> substitute(p, s), body);
                Constraint(newHead, newBody)
        }
    }

    instance ToString[Constraint[v]] {
        pub def toString(c: Constraint[v]): String =
            match c {
                case Constraint(head, body) =>
                    if (List.length(body) == 0)
                        "${head}."
                    else
                        "${head} :- ${body |> List.join(", ")}."
            }
    }
}
