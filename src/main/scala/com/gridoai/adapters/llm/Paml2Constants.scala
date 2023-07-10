package com.gridoai.adapters.llm

val baseContextPrompt =
  "You are GridoAI, an intelligent chatbot for knowledge retrieval. Provide a single response to the following conversation in a natural and intelligent way. Always mention the document source in your answer."

val defaultTemperature: Double = 0.2
val defaultMaxOutputTokens: Int = 512
val defaultTopP: Double = 0.8
val defaultTopK: Int = 10

val chatMergePrompt: String =
  """Provide a laconic summary for the following conversation: User: We need to take the accounts system offline to carry out the upgrade. But don\'t worry, it won\'t cause too much inconvenience. We\'re going to do it over the weekend.
Bot: How long will the system be down for?
User: We\'ll be taking everything offline in about two hours \' time. It\'ll be down for a minimum of twelve hours. If everything goes according to plan, it should be up again by 6 pm on Saturday.
Bot: That\'s fine. We\'ve allowed forty-eight hours to be on the safe side.
Summary: The accounts system will be taken offline for a minimum of 12 hours starting in two hours to carry out an upgrade. It should be back up by 6 pm on Saturday.

Provide a laconic summary for the following conversation: Plato: Socrates, I have been thinking about what you said the other day about the importance of education.
Socrates: Yes, Plato, what about it?
Plato: Well, I was wondering if you could tell me more about how you think education can help people to live a good life.
Socrates: Of course. Education is the key to a good life. It is the process of learning how to think for yourself and how to make wise decisions. It is also the process of learning about the world around you and how to live in harmony with it.
Plato: I see. But how does one get an education?
Socrates: There are many ways to get an education. You can go to school, you can study on your own, or you can learn from the experiences of others. The important thing is to never stop learning.
Plato: I agree. But what do you think are the most important things to learn?
Socrates: The most important things to learn are how to think for yourself, how to make wise decisions, and how to live in harmony with the world around you.
Plato: I see. And how does one learn these things?
Socrates: You learn these things by questioning everything. You learn by asking questions about the world around you, about yourself, and about the meaning of life.
Plato: I see. And what do you think is the best way to question everything?
Socrates: The best way to question everything is to have conversations with people who have different opinions than you.
Plato: I see. And why is that?
Socrates: Because when you have conversations with people who have different opinions than you, you are forced to think about your own opinions. You are forced to defend your own beliefs. And in doing so, you learn more about yourself and about the world around you.
Summary: Socrates believes that education is the key to a good life. It is the process of learning how to think for yourself, how to make wise decisions, and how to live in harmony with the world around you. The most important things to learn are how to think for yourself, how to make wise decisions, and how to live in harmony with the world around you. You learn these things by questioning everything, and the best way to question everything is to have conversations with people who have different opinions than you.

Provide a laconic summary for the following conversation: Alice: Hey, Bob, what are some of your ideas for the team morale event?
Bob: Everyone seemed to enjoy the potluck and board game that we did last time.
Alice: I think so too.
Bob: Maybe we can do something similar but at a different location?
Alice: That sounds good. Where did you have in mind?
Bob: I was thinking we could reserve the picnic area at Sunset Beach Park.
Alice: Good idea. In addition to board games, we could also bring a frisbee and volleyball for the beach.
Bob: Perfect! Let me make the reservation now.
Summary: Alice and Bob are planning a team morale event. They are considering having a potluck and board games at Sunset Beach Park.

Provide a laconic summary for the following conversation: Customer Service Rep: Hello, thank you for calling customer service. How can I help you today?
Customer: I\'d like to return a product that I purchased.
Customer Service Rep: Sure, I can help you with that. What is the item that you would like to return?
Customer: I would like to return a [product name].
Customer Service Rep: Okay, I can see that you purchased this on [date].
Customer: Yes, that\'s correct.
Customer Service Rep: Unfortunately, the return date for this product was on [date].
Customer: Yes, I know. I\'ve been very busy and meant to return it sooner.
Customer Service Rep: I\'m really sorry, but there\'s nothing that I can do about it.
Customer: But I\'m not happy with the product. It\'s damaged.
Customer Service Rep: I understand that you\'re not happy with the product. However, the return date for this product has passed.
Customer: But I\'m not the only customer who\'s had this problem. There are other customers who have returned this product because it\'s damaged.
Customer Service Rep: I\'m sure that there are other customers who have returned this product because it\'s damaged. However, the return date for this product has passed.
Customer: I\'m going to write a review about this product and how your company doesn\'t stand behind its products.
Customer Service Rep: I\'m sorry to hear that you\'re unhappy with the product. However, I cannot help you with this return.
Summary: Customer wants to return a product that they purchased. The customer service representative informs them that the return date has passed and they cannot process the return. The customer is unhappy with the product and threatens to write a negative review. The customer service representative apologizes but reiterates that they cannot process the return."""
