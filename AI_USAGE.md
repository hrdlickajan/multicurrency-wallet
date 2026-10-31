## AI Usage

### Which AI tools you used and for what.
 - Github Copilot, GPT 6.1 Sol (Grilling and Implementation) and Claude Sonnet 5.5 (Review)
### How you steered them (key prompts, instructions, or agent configuration files you committed, if any).
 1. Grilling session with grill-me skill - about 30 questions
 2. Product description with write-a-prd skill
 3. Implementation with ponytail agent
 4. Code review from Sonnet
### One or two places where the AI was wrong, incomplete, or over-engineered, and what you did about it.
 - The PRD itself was too complex ⇒ complex code (probably my fault too), I had to go through the code and prompt the agent after implementation to simplify and/or explain things.
 - I had to reread the documentation and make the agent to write simpler sentences
### What you deliberately wrote or decided yourself.
- Contracts - what to return to the client
- CNB response caching and missing rates
- Hardcoded currency codes