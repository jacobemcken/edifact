#  EDIFACT parsing - Clojure library

The library parses [Edifact data][1] into a predictable data strcuture while at
the same time verifying both semantics and data. Transforming the data into
"pretty" hashmaps is a job left to you, at least it should be manageble now that
all the error cases are handled by the library.

The following usage examples will show the [happy path][2] (when nothing goes
wrong), followed by how error states are handled.

[1]: https://en.wikipedia.org/wiki/EDIFACT
[2]: https://en.wikipedia.org/wiki/Happy_path


## Usage examples

### Happy path

First include the library:

```clojure
(ns my.app.edifact
  (:require [dk.emcken.edifact.tokenizer :refer [tokenize]]
            [dk.emcken.edifact.schema :refer [schema-based-on-unh schemafy]]))
```

Lets play with one of the simple interchanges first. Prior knowledge to EDIFACT
is recommended.

The following example is constructed from reading Nordea's [Implementation
Guideline for CONTRL][3]. Notice how one of the seperator charaters are escaped
and used in the text `NORDEASPECIAL+TEST`:

[3]: https://www.nordea.com/Images/33-47010/eGateway-CONTRL_12.pdf

```clojure
(def contrl "
UNA:+.? '
UNB+IATB:1+6XPPC:ZZ+LHPPC:ZZ+940101:0950+1'
UNH+1001+CONTRL:D:3:UN'
UCI+131+333666999+NORDEASPECIAL?+TEST:ZZ+7'
UCM+1+PAYMUL:D:96A:UN+4+13+UNH+2:4'
UCS+17+16'
UNT+5+1'
UNZ+1+1
")
```

The tokenizer respects release (escape) characters :

```clojure
(clojure.pprint/pprint (tokenize contrl))
((["UNB"]
  ["IATB" "1"]
  ["6XPPC" "ZZ"]
  ["LHPPC" "ZZ"]
  ["940101" "0950"]
  ["1"])
 (["UNH"] ["1001"] ["CONTRL" "D" "3" "UN"])
 (["UCI"] ["131"] ["333666999"] ["NORDEASPECIAL+TEST" "ZZ"] ["7"])
 (["UCM"]
  ["1"]
  ["PAYMUL" "D" "96A" "UN"]
  ["4"]
  ["13"]
  ["UNH"]
  ["2" "4"])
 (["UCS"] ["17"] ["16"])
 (["UNT"] ["5"] ["1"])
 (["UNZ"] ["1"] ["1"]))
nil
```


Now that the data has been tokenized it is time to apply a schema.

The following was taken from the documentaiton linked above, which describes how
this particular EDIFACT message is expected to look:

```
UNH Message header                            M1 M1
UCI Interchange response                      M1 M1
    ------ Segment group 1  ------------ C999999 O999999------+
UCM Message response                          M1 M1           ¦
                                                              ¦
    ------ Segment group 2  --------------- C999 O999 -------+¦
UCS Segment error indication                  M1 M1          ¦¦
UCD Data element error indication            C99 O99 ---------+
    ------ Segment group 3  ------------ C999999  0 ----------+
UCF Functional group response                 M1  0           ¦
                                                              ¦
    ------ Segment group 4  ------------ C999999  0 ---------+¦
UCM Message response                          M1  0          ¦¦
                                                             ¦¦
    ------ Segment group 5  --------------- C999  0 --------+¦¦
UCS Segment error indication                  M1  0         ¦¦¦
UCD Data element error indication            C99  0 ----------+
UNT Message trailer                           M1 M1
```

Now we take this documentation and turn it into a configuration describing a
valid CONTRL message (for this example segment group 3,4 and 5 are ignored):

```clojure
(defmethod schema-based-on-unh ["CONTRL" "D" "3" "UN"]
  [_]
  '(["UCI" [1 1]]
    [:sg1 [0 999999]
     (["UCM" [1 1]]
      [:sg2 [0 999]
       (["UCS" [1 1]]
        ["UCD" [0 99]])])]))
```

Now that `schema-based-on-unh` has been extended to know of the CONTRL message,
it is possible to apply the schema using the following:

```clojure
(clojure.pprint/pprint (schemafy (tokenize contrl) schema-based-on-unh))
[nil
 [(["UNB"]
   ["IATB" "1"]
   ["6XPPC" "ZZ"]
   ["LHPPC" "ZZ"]
   ["940101" "0950"]
   ["1"])
  [:sg0
   [(["UNH"] ["1001"] ["CONTRL" "D" "3" "UN"])
    (["UCI"] ["131"] ["333666999"] ["NORDEASPECIAL+TEST" "ZZ"] ["7"])
    [:sg1
     [(["UCM"]
       ["1"]
       ["PAYMUL" "D" "96A" "UN"]
       ["4"]
       ["13"]
       ["UNH"]
       ["2" "4"])]]
    (["UNT"] ["5"] ["1"])]]
  (["UNZ"] ["1"] ["1"])]]
```

The first value in the returned list is the remaining segments (`nil` means
none), while the second value is the schemafied version of the tokenized EDIFACT
representation.


### Validation

The library can validate an edifact interchange in two ways:

- Semantics
- Data

The semantics describe the structure of the interchange like segment grouping,
order, repetition and whether or not those are mandatory or optional. Basically
it describes "where" to find the "what" (a.k.a. the data).

The validation will be able to tell *exactly* where in the edifact interchange
garbage was detected.


#### Data

The above example didn't include any data validation. Lets make the validaiton a
bit more strict. Looking at the `UCI` segment:
```
UCI+131+333666999+NORDEASPECIAL?+TEST:ZZ+7'
```

The first component in the first element is a "control reference to an
interchange number" (`131`). For the sake of this example lets assume that the
control reference can only be numbers.

Each component is validated in isolation. The nature of the validation is
provided by a 2-dimensional vector representing elements and their components.

Redefine the schema to enable validation on the "control reference to an
interchange number":

```clojure
(defmethod schema-based-on-unh ["CONTRL" "D" "3" "UN"]
  [_]
  '(["UCI" [1 1] [[#"^\d+$"]]]
    [:sg1 [0 999999]
     (["UCM" [1 1]]
      [:sg2 [0 999]
       (["UCS" [1 1]]
        ["UCD" [0 99]])])]))
```

Being given the following `UCI` segment:
```
UCI+ABC+333666999+NORDEASPECIAL?+TEST:ZZ+7'
     └─ non numbers not allowed
```

Would cause the following exception to be thrown:

```
Unhandled clojure.lang.ExceptionInfo
Unable to validate segment
{:component 1,
 :element 2,
 :error "Must match regular expression",
 :type :dk.emcken.edifact.schema/validation,
 :segment 5}
```

Lets make the schema even stricter. The first component in the second element is
a "sender" (`333666999`). A sender can be up to 35 alpha-numeric characters but
never empty.

For this a helper function would be nice:

```clojure
(defn an..35m
  "Ensures a maximum of 35 alpha-numeric characters that is mandatory (not empty)"
  [component]
  (and
    (not-empty component)
    (< (count component) 35)))
```

Now redefine the schema again:

```clojure
(defmethod schema-based-on-unh ["CONTRL" "D" "3" "UN"]
  [_]
  '(["UCI" [1 1] [[#"^\d+$"] [an..35m]]]
    [:sg1 [0 999999]
     (["UCM" [1 1]]
      [:sg2 [0 999]
       (["UCS" [1 1]]
        ["UCD" [0 99]])])]))
```

Being given either of the following 2 `UCI` segments an execption would be
thrown:

```
UCI+131+333666999012345678901234567890123456+NORDEASPECIAL?+TEST:ZZ+7'
        └────── longer than 35 chars ──────┘

UCI+131++NORDEASPECIAL?+TEST:ZZ+7'
        └─ empty sender not allowed
```


#### Semantics

Lets start out easy by inserting a wrong segment where a mandatory segment is
expected. After a `UCI` segment either a `UCM` (in segment group 1) or `UNT`
(the message trailer) is expected. Addind a madeup `UCN` segment there will
break that assumption:

```clojure
(def contrl "
UNA:+.? '
UNB+IATB:1+6XPPC:ZZ+LHPPC:ZZ+940101:0950+1'
UNH+1001+CONTRL:D:3:UN'
UCI+131+333666999+NORDEASPECIAL?+TEST:ZZ+7'
UCN'
UCM+1+PAYMUL:D:96A:UN+4+13+UNH+2:4'
UCS+17+16'
UNT+5+1'
UNZ+1+1
")
```

This will cause an exception to be trown:

```
Unhandled clojure.lang.ExceptionInfo
Unable to find mandatory segment: :sg0
{:type :dk.emcken.edifact.schema/structure,
 :segment 7,
 :error "Unable to find mandatory segment: :sg0"}
```

The count (`7`) is the segment number from the bottom and up. Which means that
at `UNH` the paser wasn't able to find a valid segment group. The rules for
`:sg0` states that it must end with a `UNT` which it never found.

```
✓ UNA:+.? '
✓ UNB+IATB:1+6XPPC:ZZ+LHPPC:ZZ+940101:0950+1'
1 UNH+1001+CONTRL:D:3:UN'
2 UCI+131+333666999+NORDEASPECIAL?+TEST:ZZ+7'
3 UCN'
4 UCM+1+PAYMUL:D:96A:UN+4+13+UNH+2:4'
5 UCS+17+16'
6 UNT+5+1'
7 UNZ+1+1
```

If you deal with very simple messages i.e. interchanges without multiple
messages or messages without optional segment groups the schema above can be
simplified (which can cause the error segment to come out different).


## UNA - Service String advice

The library respects a non-standard UNA segment (different delimiter and escape
charaters than: `UNA:+.? '`).


## License

Copyright © 2018

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
