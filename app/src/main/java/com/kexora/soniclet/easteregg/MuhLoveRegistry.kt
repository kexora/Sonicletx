package com.kexora.soniclet.easteregg

import android.content.Context
import java.util.Calendar
import java.util.concurrent.TimeUnit
import kotlin.math.max

data class SpecialDateInfo(
    val month: Int, // Calendar.JANUARY (0) to Calendar.DECEMBER (11)
    val dayOfMonth: Int,
    val birthYear: Int? = null,
    val baseYear: Int? = null,
    val title: String,
    val subtitle: String,
    val badgeText: String,
    val celebrationIcon: String = "💖",
    val messages: List<String>
)

object MuhLoveRegistry {

    // Configured exact reference years:
    // Birthday: 21 March 2007
    // First Conversation: 1 July 2024
    // Proposal Date: 23 November 2025

    /**
     * Strictly verifies whether Muh Mode (Easter Egg Edition) is currently unlocked & activated.
     * Only users who have entered the secret passcode in About Settings have this flag set to true.
     */
    fun isMuhModeActive(context: Context): Boolean {
        val prefsAudio = context.getSharedPreferences("soniclet_audio_prefs", Context.MODE_PRIVATE)
        if (prefsAudio.getBoolean("setting_moh_mode_activated", false)) return true
        val prefsMain = context.getSharedPreferences("soniclet_preferences", Context.MODE_PRIVATE)
        return prefsMain.getBoolean("setting_moh_mode_activated", false)
    }

    fun getSpecialDates(calendar: Calendar = Calendar.getInstance()): List<SpecialDateInfo> {
        val currentYear = calendar.get(Calendar.YEAR)

        // 1. Birthday: 21 March 2007
        val age = max(currentYear - 2007, 1)
        val birthdayDate = SpecialDateInfo(
            month = Calendar.MARCH,
            dayOfMonth = 21,
            birthYear = 2007,
            title = "Happy $age${getOrdinalSuffix(age)} Birthday Karishma! 🎂✨",
            subtitle = "Today you turn $age! Celebrating the most beautiful soul in my universe 💖",
            badgeText = "$age YEARS OLD TODAY! 🎂",
            celebrationIcon = "🎂",
            messages = listOf(
                // Cute & Romantic
                "Sach batau toh tumhare aane se meri duniya thodi zyada bright ho gayi hai. Happy Birthday Karishma! 🌸✨",
                "Aaj poore $age saal ki ho gayi ho, par mere liye hamesha wahi cute si ziddi bachhi rahogi! ❤️🎂",
                "Happy Birthday meri personal sunshine! Hamesha aise hi khilkhilati rehna. ☀️💖",
                "Agar 21 March ka din calendar se hata diya jaye, toh meri life ka sabse favourite reason hi chhin jayega. Happy $age${getOrdinalSuffix(age)} Birthday! 🎈",
                "Duniya ke liye tum $age saal ki ho gayi ho, par mere liye tum roz aur zyada pyaari hoti ja rahi ho. 🥺❤️",
                "Happy Birthday Karishma! Bas tum hasti raha karo, baaki sab mai handle kar lunga. 🫂💫",
                "Aaj ke din bas ek hi wish hai—tumhari aankhon me kabhi aansu na aaye, siwaye bohot zyada hasne ke! 🎂🌷",
                "Tumhe dekh kar lagta hai Rab ne fursat me koi bohot khoobsurat cheez banayi thi. Happy Birthday meri jaan! ✨❤️",
                "Mujhe lagta tha birthdays bas ek regular din hote hain... jab tak tumse nahi mila tha. Happy $age${getOrdinalSuffix(age)} Birthday! 🥂💖",
                "Happy Birthday Karishma! Kabhi kabhi lagta hai tum koi magic ho jo sach me exist karti hai. 🪄✨",

                // Playful, Teasing & Fun
                "Happy Birthday! $age saal ki toh ho gayi ho, par dimaag kab bada hoga tumhara? Chalo koi nahi, waise bhi cute lagti ho! 😜🎂",
                "Aaj tumhara birthday hai, isliye din bhar tumhari saari daant chupchap sununga! Happy $age${getOrdinalSuffix(age)} Birthday drama queen! 👑😂",
                "Officially ek saal aur badi ho gayi ho, par mere aage ro ro ke zidd karna band mat karna. Happy Birthday Karishma! 🍰❤️",
                "Tumhe lagta hoga ki tum bohot mature ho gayi ho $age me... par mujhe sach pata hai! Happy Birthday pagli! 🐒🎉",
                "Happy Birthday! Cake ka sabse bada piece tumhara, par baaki bacha hua saara mera, deal? 🎂🤤",
                "Bhagwan ne tumhe bohot fursat se banaya, par thoda sa nautanki masala galti se zyada gir gaya tha! Happy Birthday drama queen! 🎭❤️",
                "Aaj ke din bas itna kahunga: tum jaisi ho, waise hi rehna. Thodi crack, thodi pagal, par bilkul perfect! 🎈🥳",
                "Happy $age${getOrdinalSuffix(age)} Birthday! Aise hi mera khoon peeti raho, mujhe aadat ho gayi hai. 🧛♀️🩸❤️",
                "Cake kaato, candle bujhao, aur jaldi se meri party ready rakho! Happy Birthday Karishma! 🥳🎂",
                "Tum $age saal ki ho gayi ho, matlab ab tumhari harkaton ko 'bachpana' bolkar bacha nahi paunga! Happy Birthday! 😂✨",

                // Deep, Emotional & Heartfelt
                "Zindagi me bohot log aate jaate hain, par tumhara hona mere dil ko sabse zyada sukoon deta hai. Happy Birthday Karishma. 🤍✨",
                "Bas itna kehna tha ki tum meri life ka sabse khoobsurat hissa ho. Happy $age${getOrdinalSuffix(age)} Birthday! 🕊️❤️",
                "Main har din shukr karta hoon ki us 21 March ko tum is duniya me aayi thi. Happy Birthday meri jaan. 🌸💫",
                "Tumhari hasi me ek aisi baat hai jo mera pura din theek kar deti hai. Aise hi hamesha muskurati rehna. 💖🎂",
                "Dua hai ki is $age${getOrdinalSuffix(age)} saal me tumhe wo sab mile jo tumne chupke se band aankhon se maanga ho. Happy Birthday! 🤲✨",
                "Chahe kitne bhi saal guzar jayein, tum mere liye hamesha wahi pehla pyaara ehsaas rahogi. Happy Birthday Karishma. 🌹❤️",
                "Tum sirf ek insaan nahi ho, tum mera ghar jaisa sukoon ho. Happy $age${getOrdinalSuffix(age)} Birthday. 🏡🤍",
                "Zindagi mushkil ho sakti hai, par jab tum paas hoti ho toh lagta hai sab theek ho jayega. Happy Birthday. 🫂✨",
                "I hope you always see yourself the way I see you—pure, beautiful, and absolutely one of a kind. Happy Birthday! 💫💖",
                "Happy Birthday Karishma. Tum jaisi ho, jahan ho, bas hamesha mere dil ke sabse kareeb rehna. ♾️❤️",

                // Daily Casual & Natural Vibe
                "Happy Birthday Karishma! Aaj ka poora din bas tumhara hai, jo bologe wahi hoga. 🎂🎉",
                "Subah se bas yahi soch raha tha ki aaj meri sabse favourite ladki ka birthday hai! Stay blessed hamesha. 🌟❤️",
                "Happy $age${getOrdinalSuffix(age)} Birthday! Tumhe birthday wish karna mere poore saal ka best task hota hai. 🧁🎈",
                "Aapko birthday ki dher saari wishes! Aaj toh thoda sa treat banta hai na? 😋🍰",
                "Happy Birthday! Tumhari har ek photo me wo jo cute si smile hoti hai, wo kabhi kam na ho. 📸💖",
                "Aaj tumhare liye poora din special bana ke rahenge. Happy $age${getOrdinalSuffix(age)} Birthday meri dost, meri jaan! 🎁✨",
                "Happy Birthday Karishma! Koshish karunga ki aaj tumhara ek bhi baar mood kharab na ho. 🤞😂❤️",
                "Tumhe pata hai aaj kaun sa din hai? Wahi din jab duniya thodi aur pyaari bani thi! Happy Birthday! 🌍💫",
                "Chalo ab jaldi se ready ho jao, birthday girl ko special feel karwana hai aaj! Happy Birthday! 👗💄🎉",
                "Happy $age${getOrdinalSuffix(age)} Birthday! Hamesha aage badhti raho, meri duayein aur support hamesha tumhare sath hain. 🚀❤️",

                // Short, Sweet & Punchy
                "Happy Birthday Karishma! Bas tum hamesha khush raho, that's all I want. 🌸🤍",
                "Tum meri sabse pyari aadat ho. Happy $age${getOrdinalSuffix(age)} Birthday! 🧸❤️",
                "Keep shining, keep smiling. Happy Birthday Karishma! ✨🎂",
                "Duniya ki saari khushiyan ek taraf, aur tumhari smile ek taraf. Happy Birthday! 💖",
                "Tu meri jaan hai, aur hamesha rahegi. Happy $age${getOrdinalSuffix(age)} Birthday! 🫂❤️",
                "Cheers to you turning $age today! Always proud of you. 🥂✨",
                "Happy Birthday meri sabse pyaari Karishma! Hamesha aise hi chamakti raho. 🌟",
                "One year older, ten times cuter! Happy Birthday! 🥰🍰",
                "Har saal tumhare sath celebrate karna chahta hoon. Happy Birthday meri jaan. 💍❤️",
                "Today is all about you! Happy $age${getOrdinalSuffix(age)} Birthday Karishma! 🎂🎉",

                // Playful, Quirky & Teasing (Part 2)
                "Happy Birthday! Aaj galti se bhi gussa mat hona, saara makeup kharab ho jayega tumhara! 😜💄",
                "Tum chahe $age saal ki ho jao ya 80 ki, bina baat ke muh phulana tumse chhutne se raha. Happy Birthday Karishma! 😤❤️",
                "Tumhari har baat sunne ke liye aaj din bhar ka patience recharge karwa liya hai maine. Happy $age${getOrdinalSuffix(age)} Birthday! 🔋😂",
                "Happy Birthday! Aaj tumse ek bhi argument nahi jeetunga... waise bhi roz kahan jeet pata hoon! 🤷♂️❤️",
                "Ek saal aur badi ho gayi ho, par please wo reels bhej-bhej kar pareshaan karna band mat karna! Happy Birthday! 📱✨",
                "Happy Birthday Karishma! Itni pyaari ho tum, bas kabhi-kabhi dimaag ghar par bhool kar chali aati ho! 🧊🐒",
                "Happy $age${getOrdinalSuffix(age)} Birthday! Aaj jitna khana hai khao, kal se fir se 'main moti ho gayi hoon' bolne ki permission hai! 🍕😋",
                "Bhagwan kare is saal tumhara phone charger hamesha tumhari bed side wale plug me lage! Happy Birthday Karishma! 🔌🎂",
                "Happy Birthday us ladki ko jo har doosre din bolti hai 'mujhe kuch samajh nahi aa raha'. Dil se wish kar raha hoon! 🤦♂️❤️",
                "Aaj tum jo bologe wahi hoga... bas ye mat kehna ki treat tum nahi dogi! Happy $age${getOrdinalSuffix(age)} Birthday! 💸🍰",
                "Happy Birthday meri constant headache aur meri sabse badi khushi... dono tum hi ho! 🤕❤️",
                "Tumhe lagta hai tum bohot normal ho, par trust me tum poori ki poori cartoon ho... aur meri sabse favourite bhi! Happy Birthday! 🎨❤️",
                "21 March ko ek chhota sa tufaan paida hua tha jo aaj $age saal ka ho gaya hai! Happy Birthday tufaan! 🌪️🎂",
                "Happy Birthday! Aaj tumhare har bekaar joke par bhi bina acting kiye zor se hasunga, promise! 🤝😂",
                "Agar cuteness par koi tax lagta na, toh tum par ab tak bohot bada karza hota! Happy $age${getOrdinalSuffix(age)} Birthday! 💸🧸",

                // Late-Night, Soft & Affectionate
                "12 baj gaye hain aur sabse pehla khayal bas tumhara tha. Happy Birthday Karishma, tum sach me bohot khaas ho. 🌙✨",
                "Kaash main abhi wahan hota aur bas ek tight hug dekar bolta—Happy Birthday meri jaan. 🫂🤍",
                "Tumhare sath chupchap baithna bhi duniya ki sabse achhi conversation lagti hai. Happy $age${getOrdinalSuffix(age)} Birthday. ☕💫",
                "Happy Birthday Karishma! Tumhari aawaz sun kar din bhar ki saari thakan gayab ho jaati hai. 🎶❤️",
                "Mujhe nahi pata aage kya hoga, par har saal tumhara birthday aise hi wish karna chahta hoon. Happy Birthday! 🕊️",
                "Happy $age${getOrdinalSuffix(age)} Birthday! Tumhe dekhta hoon toh lagta hai haan, khuda ne kisi ko toh bohot pyaar se banaya hai mere liye. 🥺❤️",
                "Kuch log zindagi me aakar use bas aasan aur khoobsurat bana dete hain... tum wahi ho. Happy Birthday Karishma. 🌸",
                "Tumhare bina meri har baat thodi aadhi rehti hai. Happy Birthday meri sabse pyaari aadat! 🫂✨",
                "Bas itna chahta hoon ki tumhari zindagi me sukoon hamesha rahe. Happy $age${getOrdinalSuffix(age)} Birthday. 🕯️🤍",
                "Happy Birthday to the girl who lives rent-free in my head 24/7! Karishma, tum meri sabse pyari feeling ho. 💭❤️",

                // Growing Up, Proud & Genuine
                "Pata hi nahi chala kab tum itni jaldi $age saal ki ho gayi! Tumhe roz grow karte dekh kar bohot proud feel hota hai. Happy Birthday! 🌱✨",
                "Tum jitni bahar se sundar ho, usse 100 guna zyada andar se saaf dil ki ho. Happy $age${getOrdinalSuffix(age)} Birthday Karishma! 💎",
                "Zindagi me chahe kitni bhi cheezein change ho jayein, tumhara mere liye wo soft corner hamesha waisa hi rehna chahiye. Happy Birthday! ❤️",
                "Happy Birthday! Apne saare dreams poore karna, aur jab kabhi thak jao toh yaad rakhna main hamesha khada hoon peechhe. 🛡️🌟",
                "Is $age${getOrdinalSuffix(age)} saal me tum jo bhi chaho wo tumhe mile, bas mujhse chhutkara nahi milega! 😜❤️",
                "Happy Birthday Karishma! Tumhari honesty aur tumhara ye masoom bachpana hi tumhari sabse badi taqat hai. 🦸♀️💖",
                "Happy $age${getOrdinalSuffix(age)} Birthday! Har saal tum thodi aur samajhdaar aur thodi aur khoobsurat lagne lagti ho. 🌹",
                "Zindagi ka har naya chapter tumhare sath aur zyada interesting lagta hai. Happy Birthday to my favourite person! 📖✨",
                "Happy Birthday Karishma! Kabhi khud par doubt mat karna, tum jitna sochti ho usse kahin zyada strong ho. 💪🤍",
                "Har din special hota hai, par 21 March meri life ke calendar me hamesha red marker se circled rehta hai. Happy Birthday! 🗓️❤️",

                // Filmy, Warm & Relatable
                "Filmon me bolte hain na 'tum jaisi ladkiyan dhoondne se bhi nahi milti', ab jaakar samajh aaya sach bolte the! Happy $age${getOrdinalSuffix(age)} Birthday! 🎬❤️",
                "Happy Birthday Karishma! Tumhe dekh kar dil wahi puraane 90s ke romantic gaane gane lagta hai. 📻🎶",
                "Cake kaatne se pehle wish jo bhi maango, usme chupke se mera naam add karna mat bhoolna! Happy Birthday! 🎂✨",
                "Main bohot zyada express nahi kar pata, par sach me tumhare bina sab thoda feeka lagta hai. Happy Birthday Karishma! 🥺🌸",
                "Happy $age${getOrdinalSuffix(age)} Birthday! Aaj ki sabse pehli aur sabse badi chocolate meri taraf se pakki. 🍫🎉",
                "Happy Birthday Karishma! Dua hai tumhari saari overthinking gayab ho jaye aur tum bas khush raho! 🧘♀️✨",
                "Chalo ek saal aur bada ho gaya rishta humara, matlab ab tum par mera haq thoda aur badh gaya! Happy $age${getOrdinalSuffix(age)} Birthday! 😉❤️",
                "Tumhara birthday hai toh lagta hai jaise mera koi personal festival ho! Happy Birthday meri jaan! 🎉💖",
                "Tumhare sath beeta har lamha phone ki gallery me nahi, dil ke sabse safe folder me saved hai. Happy Birthday Karishma! 📁❤️",
                "Happy $age${getOrdinalSuffix(age)} Birthday! Hamesha aise hi bindass rehna, duniya kya bolti hai uspe dhyan mat dena. 🌈💫",

                // Punchy, Direct & Emotional
                "Duniya me itne log hain, par kisi ki baat itna asar nahi karti jitna tumhari ek smile karti hai. Happy Birthday! 🌸",
                "Happy Birthday to my human diary! Jisko mere baare me mujhse zyada pata hai. Love you Karishma! 📖❤️",
                "Aaj ka din celebrate karna toh banta hai, aakhir meri duniya me tumhara hi toh raaj hai! Happy $age${getOrdinalSuffix(age)} Birthday! 👑✨",
                "Happy Birthday Karishma! Hamesha sath rehna, chahe ladai ho ya pyaar. Tum mere liye sabse khaas ho. 🤍♾️",
                "Har saal bas yahi dua nikalti hai—tum jahan bhi raho, hamesha aise hi hasti-muskurati raho. Happy Birthday! 🎂✨"
            )
        )

        // 2. First Conversation: 1 July 2024
        val talkYears = max(currentYear - 2024, 0)
        // Calculate days passed since 1 July 2024
        val firstTalkCal = Calendar.getInstance().apply {
            set(2024, Calendar.JULY, 1, 0, 0, 0)
        }
        val daysDiff = max(
            TimeUnit.MILLISECONDS.toDays(calendar.timeInMillis - firstTalkCal.timeInMillis).toInt(),
            1
        )
        val talkSubtitle = if (talkYears >= 1) {
            "1st July — $talkYears year${if (talkYears > 1) "s" else ""} ($daysDiff days) since our very first conversation 📞❤️"
        } else {
            "1st July — $daysDiff days since our very first conversation! ✨"
        }
        val firstTalkDate = SpecialDateInfo(
            month = Calendar.JULY,
            dayOfMonth = 1,
            baseYear = 2024,
            title = "First Talk Anniversary ($talkYears Year${if (talkYears > 1) "s" else ""}) 📞❤️",
            subtitle = talkSubtitle,
            badgeText = "$talkYears YEAR${if (talkYears > 1) "S" else ""} TALKING 📞",
            celebrationIcon = "📞",
            messages = listOf(
                // First Text & Typing Nostalgia
                "1st July 2024... wo pehla message type karke send karne se pehle 10 baar socha tha! Happy First Chat Anniversary Karishma! 💬❤️",
                "Wo pehla 'Hi' jo tumne bheja tha na, usne meri poori duniya badal kar rakh di! 🕊️✨",
                "Screen par 'Karishma is typing...' dekh kar jo pet me butterflies aati thi, aaj poore $talkYears saal baad bhi waisi hi feeling aati hai! 🦋📲",
                "Kisko pata tha ki 1 July ko aaya wo ek chhota sa notification meri zindagi ka sabse khaas message ban jayega! 💌💫",
                "Aaj poore $daysDiff din ho gaye humare us pehle text ko, par tumhare texts ka wait aaj bhi utni hi excitement se karta hoon. ⏳💖",
                "Wo pehli chat jab main baar-baar delete karke naye replies likh raha tha taaki stupid na lagu! Happy $talkYears Year${if (talkYears > 1) "s" else ""}! 🙈😂",
                "1st July 2024 ko bas ek chat open hui thi, pata nahi tha ki ye conversation kabhi band hi nahi hogi. 📱🤍",
                "Pehli baar jab tumhara text notification screen pe pop hua tha na, chehre par ajeeb si smile aa gayi thi. Happy Anniversary! 🌸✨",
                "Aaj humari pehli chat ko $daysDiff din ho gaye! Tab se lekar aaj tak mera phone sirf tumhare messages ke liye vibrate hota hai. 🔔❤️",
                "Wo pehla text conversation humari kahani ka starting point tha. Thank you for that first text Karishma! ✍️💫",

                // Late-Night Chats & Cute Scenarios
                "1st July ke baad se meri neend ka schedule jo kharab hua hai, wo aaj $talkYears saal baad bhi sudhar nahi paya! 🥱🛌❤️",
                "Wo ghanton screen par ungliyan chalana aur 3 baje tak bina kisi reason ke text karte rehna... Happy Chat Anniversary! 🌙💬",
                "Happy Anniversary Karishma! Tumhara text aate hi mera sabse pehla kaam hota hai baaki saari duniya ko ignore karna. 📵🥰",
                "1 July 2024 se shuru hua ye silsila... aaj tak meri gallery tumhare chats ke cute screenshots se bhari padi hai! 📸❤️",
                "Yaad hai shuru-shuru me hum kitne formal emojis bhejte the? Aur aaj stickers aur bekaar memes ki barsaat hoti hai! 😂📦",
                "Duniya ke liye wo bas ek WhatsApp message tha, par mere liye wo meri kismat ka darwaza khulna tha. Happy $talkYears Year${if (talkYears > 1) "s" else ""}! 🚪✨",
                "Aaj ke din bas itna kahunga—jab bhi tumhara 'seen' aata hai na, dil thoda sa relax ho jata hai. 💙🤍",
                "1st July ko pehli baar chat karte waqt darr lag raha tha ki kahin conversation dry na ho jaye... aur aaj dekho, chup hone ka naam hi nahi lete hum! 🌊😂",
                "Happy Chat Anniversary! Tumhare texts mere din bhar ke stress ka sabse sasta aur best ilaj hain. 💊✨",
                "Wo pehli raat jab hum dono phone haath me pakde-pakde so gaye the... wahi se samajh aa gaya tha ki matter serious hai! 😴📱❤️",

                // Playful & Teasing Chat Vibes
                "Pehli chat me kitna achha bacha ban kar type kar rahi thi! Tab mujhe bilkul andaza nahi tha ki tum itni badi nautanki niklogi! 🎭😜",
                "1st July 2024 se lekar aaj tak, ek saal me $daysDiff baar tumne bina baat ke 'Hmm' likh kar bheja hai! Happy Anniversary! 😤❤️",
                "Happy First Chat Anniversary! $talkYears saal ho gaye tumhare typo mistakes aur ajeeb auto-correct jhelte hue! ⌨️😂",
                "Pehli chat par kitna dhyan se soch soch kar replies aate the, aur aaj turant 10 voice notes bhej deti ho bina context ke! 🎙️🏃♂️",
                "Tumhari wahi purani aadat—khud text karke gayab ho jana! Par chalo, $talkYears saal ho gaye, ab aadat pad gayi hai. 👻❤️",
                "Happy Anniversary! Us pehle text ke baad agar tumne ghost kar diya hota na, toh mera dil wahi tut jata! Bach gaya main! 💀😂",
                "1st July 2024 ko jo text thread shuru hua tha, wo meri life ka sabse lamba aur best thread hai. 🧵❤️",
                "Aaj poore $talkYears saal ho gaye! Ab toh bata do us pehle text ko bhejne se pehle kitne dosto se consult kiya tha? 😉📲",
                "Main phone charge bas isliye karta hoon taaki tumhare texts ka reply ek second me de saku! Happy Chat Anniversary! ⚡❤️",
                "Happy Anniversary Karishma! Kabhi kabhi lagta hai mera phone tumhare texts deliver karne ke liye hi bana hai. 📦💖",

                // Deep, Soft & Emotional Text Connections
                "Pehli chat se lekar aaj tak, tumhare likhe hue har lafz me ek sukoon sa milta hai. Happy Anniversary Karishma. 🤍✨",
                "1st July 2024 ko humari baatein shuru hui thi, aur kab tum meri zindagi ki sabse pyari aadat ban gayi pata hi nahi chala. 🕊️❤️",
                "Chahe kitni bhi doori ho, tumhara ek chhota sa text bhi aisa lagta hai jaise tum mere bilkul paas baithi ho. 🫂💫",
                "Pehle anjaan the, fir ek message aaya, aur aaj $daysDiff din baad tumhare bina mera din shuru aur khatam nahi hota. 🌅🌌",
                "Tumhari bheji hui wo purani chats aaj bhi padhta hoon toh chehre par wahi pehle din jaisi smile aa jaati hai. 🥺❤️",
                "1 July 2024 ko mera inbox bhale hi simple raha ho, par mera dil us din se full ho gaya. Happy $talkYears Year${if (talkYears > 1) "s" else ""}! 💌🌸",
                "Ek simple sa message hume itna paas le aayega, ye maine kabhi sapne me bhi nahi socha tha. You are my blessing Karishma. 🤲✨",
                "Kash main 1st July 2024 ke us moment ko frame karwa pata jab tumhara pehla reply aaya tha! 🖼️💖",
                "Tumhare texts padhte waqt mere dimaag me automatically tumhari cute si aawaz bajne lagti hai. Happy Anniversary! 🎶🤍",
                "Humari chat sirf words nahi hain, isme humara poora rishta, humari hasi, aur humara pyaar chhipa hai. ♾️❤️",

                // Short, Relatable & Heartfelt
                "From that first text on 1st July 2024 to 'I can't imagine my day without texting you'—we came a long way! 🥂❤️",
                "Happy First Chat Anniversary Karishma! My favourite notification will always be your name. 🔔💖",
                "1st July 2024 ko shuru hua ye text conversation meri life ki best story ban gaya! 📖✨",
                "Bas aise hi roz bina matlab ke messages bhejte rehna hamesha. Happy $talkYears Year${if (talkYears > 1) "s" else ""}! 💬🤍",
                "Pehla message tumne kiya ho ya maine, kismat hum dono ki ban gayi! Happy Anniversary! 🍀📱",
                "Aaj $daysDiff din ho gaye humari texting journey ko, and I still smile at my screen like an idiot because of you! 🥰✨",
                "Pehli chat se lekar aaj tak, you are the easiest person to talk to. Love you Karishma! 🌸❤️",
                "Har ek text ke sath tum mere dil ke thoda aur kareeb aati gayi. Happy $talkYears Year${if (talkYears > 1) "s" else ""}! 🎈💫",
                "Aapki aur meri texting history mere phone ka sabse valuable data hai! Happy First Chat Anniversary! 💾❤️",
                "1st July 2024 was just a date until your message popped up and made it unforgettable forever. Happy Anniversary meri jaan! 💐❤️",

                // WhatsApp/Instagram Chat Quirks & Relatable Moments
                "1st July 2024... jab pehli baar tumhari profile picture zoom karke dekhi thi! Happy First Chat Anniversary Karishma! 🔍😂",
                "Pehli chat yaad hai? Jab hum dono full stops aur proper grammar ke sath type kar rahe the, jaise koi interview chal raha ho! 📑🙈",
                "Aaj poore $daysDiff din ho gaye humari chat ko, aur ab tak humne kam se kam 10,000 baar 'aur batao' bol diya hoga! 🔄❤️",
                "Wo pehli chat me jab tumhara text aata tha aur main 2 minute wait karke reply karta tha taaki desperate na lagu! Happy Anniversary! ⏱️😜",
                "1st July ke din se meri screen-time report ne jo jump maara hai, wo aaj $talkYears saal baad bhi neeche nahi aaya! 📈📱",
                "Pehli baar jab tumne chat me 'Haha' ki jagah emoji bheja tha, laga tha chalo ice break ho gayi! Happy $talkYears Year${if (talkYears > 1) "s" else ""}! 🧊😂",
                "Happy Chat Anniversary Karishma! Tumhara 'Reached home' wala text aaj bhi mere dil ko sabse zyada relax karta hai. 🏡🤍",
                "1 July 2024 se lekar aaj tak, meri chat list me tumhara naam hamesha sabse upar pin rehta hai. 📌❤️",
                "Wo pehli chat ka 'Good night' jo 2 ghante baad tak chalta raha tha... Happy First Chat Anniversary meri jaan! 🥱✨",
                "Pehli conversation me kitne sophisticated emojis use ho rahe the, aur aaj dekho bas random stickers ud rahe hote hain! 📦🐒",
                "Tumhe text karna meri muscle memory ban chuka hai, phone unlock hote hi ungliyan tumhari chat par chali jaati hain. 📲💫",
                "Happy Anniversary! Socho agar 1st July 2024 ko kisi ek ka bhi internet pack khatam hota toh kya hota? Pack recharge karwane wale ko pranaam! 📶🙏",
                "Wo pehla 'Typing...' ka suspense kisi thriller movie ke climax se kam nahi tha Karishma! Happy $talkYears Year${if (talkYears > 1) "s" else ""}! 🎬🍿",
                "1 July 2024 ko ek chhota sa conversation start hua tha, aur aaj hum dono bina kuch bole ek doosre ke emojis samajh jaate hain! 🧐❤️",
                "Pehli chat par socha tha ki 'thodi der baat karke so jaunga', par kismat ne poori raat jaagne ka plan bana rakha tha! 🌌👀",

                // Teasing, Banter & Funny Fights
                "Happy First Chat Anniversary! $talkYears saal ho gaye, par 'tum pehle so jao' ka topic aaj tak resolve nahi hua! 🛌😤",
                "1st July 2024 ko toh tum bohot sweet ban rahi thi chat pe, tab kisne bataya tha ki aage jaakar bina baat ke block karne ki dhamki dogi! 🚫😂",
                "Aaj $daysDiff din ho gaye humari chat ko! Ab toh maan lo ki pehla text tum hi bhejna chahti thi, bas pehal maine kar di! 😜💌",
                "Happy Chat Anniversary Karishma! $talkYears saal ho gaye tumhare ajeeb-o-gareeb reels ke spam dekhte hue! 📱🤦♂️❤️",
                "Pehli chat me jo ladki 'Thank you' aur 'Welcome' bol rahi thi, aaj seedha 'oye sun' se baat shuru karti hai! Character development toh dekho! 📈😂",
                "Agar humari 1st July 2024 se lekar ab tak ki chats print karwayein na, toh ek poori moti encyclopedia ban jayegi! 📚🤯",
                "Happy Anniversary! Us pehle text ke baad agar tumhara reply 24 ghante late aata, toh main wahi devdas ban gaya hota! 😂🥀",
                "Tumhari chat kholte hi screen ki brightness se zyada mera chehra chamakne lagta hai. Happy $talkYears Year${if (talkYears > 1) "s" else ""} Karishma! ✨😎",
                "Pehli chat me kitna polite andaaz tha, aur aaj text par aisi ladai hoti hai jaise do billiyan aapas me lad rahi hon! 🐱🥊❤️",
                "Happy Chat Anniversary to the girl jiska 'main 5 minute me text karti hoon' matlab poore 2 ghante hota hai! ⏳🙄❤️",

                // Soft, Emotional & Midnight Feels
                "1st July 2024... jab do bilkul alag duniye ke log ek choti si chat box me aakar mil gaye the. Happy Anniversary Karishma. 🤍✨",
                "Pehli chat se lekar aaj tak, kitne din badal gaye, par tumhara mere din ko better banana kabhi nahi badla. 🕊️🌸",
                "Kabhi-kabhi purani chat scroll karke 1st July 2024 par chala jata hoon... kitna masoom aur pyaara tha wo pehla din. 📜🥺",
                "Tumhara ek chhota sa 'Are you okay?' wala text duniya ke saare gham bhula deta hai. Happy $talkYears Year${if (talkYears > 1) "s" else ""}! 🫂❤️",
                "1 July 2024 ko meri screen par ek message aaya tha, par sach bolu toh wo mere dil me save ho gaya tha. 💌💫",
                "Duniya me lakho log roz text karte hain, par mere liye kisi ka text itna peaceful nahi hota jitna tumhara hota hai Karishma. 🕯️🤍",
                "Wo pehla din humari bonding ka beej tha, jo aaj $talkYears saal baad ek khoobsurat rishta ban chuka hai. 🌱🌹",
                "Thank you Karishma, 1st July 2024 ko us pehle message ka itne pyaar se reply karne ke liye. Tabhi se meri kismat chamak gayi. 🤲✨",
                "Tumhari bheji hui har line me ek aisi warmth hoti hai jo kitne bhi kharab din ko theek kar sakti hai. Happy Anniversary! ☕💖",
                "Humari baatein shuru bhale hi text se hui ho, par ab ye connection dilon ka ban chuka hai. Happy $daysDiff days together! ♾️🤍",

                // Deep Connection & Future Vibes
                "1st July 2024 se shuru hui ye typing journey humare haath pakadne tak hamesha aise hi chalegi. 🤝❤️",
                "Happy First Chat Anniversary! Mujhe hamesha wo pehla din yaad rahega jab laga tha ki haan, ye ladki bohot alag hai. 🌸✨",
                "Humne text par hasa hai, text par lade hain, aur text par hi ek doosre ko sambhala hai. Happy $talkYears Year${if (talkYears > 1) "s" else ""}! 🩹💖",
                "1 July 2024 ko shuru hua safar aaj yahan hai, aur main chahta hoon ye chat box humari aakhri umr tak aise hi active rahe. 👵👴📱",
                "Pehli chat se lekar aaj tak, tumne mujhe hamesha waise hi accept kiya jaisa main hoon. Love you Karishma! 💎🤍",
                "Kitni baatein jo main zabaan se bol nahi pata, wo tumhare sath text me bina jhijhak keh deta hoon. You are my safe place. 🛡️✨",
                "Happy Anniversary! Us pehle message se shuru karke humne kitne saare emotions ek doosre ke sath jee liye. 🌈💫",
                "1st July 2024 was the moment fate decided to drop the sweetest gift in my inbox. Happy $talkYears Year${if (talkYears > 1) "s" else ""}! 🎁❤️",
                "Chahe hum kitne bhi busy ho jayein, ek 'miss you' ka text humari saari dooriyan khatam kar deta hai. Happy Chat Anniversary! 💌🫂",
                "Humari kahani kisi fairytale se kam nahi hai, bas farq itna hai ki ye ek chat screen se shuru hui thi! 📖✨",

                // Sweet, Crisp & Impactful
                "1st July 2024: Best text sent, best story started. Happy Anniversary Karishma! 🥂❤️",
                "Happy First Chat Anniversary! Still blushing over your texts since 1st July 2024. 🥰📲",
                "Aaj poore $daysDiff din ho gaye! Har single text ke sath tum aur pyaari lagti gayi. 🌸💖",
                "Wo pehla message meri life ka sabse profitable investment tha! Happy $talkYears Year${if (talkYears > 1) "s" else ""}! 📈❤️",
                "Pehli chat se lekar hamesha ke liye... tum hi meri favourite ho Karishma. ♾️✨"
            )
        )

        // 3. Proposal Date: 23 November 2025
        val proposalYears = max(currentYear - 2025, 0)
        val proposalCal = Calendar.getInstance().apply {
            set(2025, Calendar.NOVEMBER, 23, 0, 0, 0)
        }
        val proposalDaysDiff = max(
            TimeUnit.MILLISECONDS.toDays(calendar.timeInMillis - proposalCal.timeInMillis).toInt(),
            1
        )
        val proposalTitle = if (proposalYears >= 1) {
            "Happy $proposalYears${getOrdinalSuffix(proposalYears)} Proposal Anniversary! 💍❤️"
        } else {
            "Proposal Day Special! 💍❤️"
        }
        val proposalSubtitle = if (proposalYears >= 1) {
            "23rd November — $proposalYears year${if (proposalYears > 1) "s" else ""} of eternal love and unconditional promises ✨"
        } else {
            "23rd November — The magical day our love story was officially sealed 💍✨"
        }
        val proposalDate = SpecialDateInfo(
            month = Calendar.NOVEMBER,
            dayOfMonth = 23,
            baseYear = 2025,
            title = proposalTitle,
            subtitle = proposalSubtitle,
            badgeText = if (proposalYears >= 1) "$proposalYears YEAR${if (proposalYears > 1) "S" else ""} OF YES! 💍" else "PROPOSAL DAY 💍",
            celebrationIcon = "💍",
            messages = listOf(
                // Riddles & "Muh" Wordplay ('Aap' Tone)
                "Paheli: Aisi kaun si jagah hai jahan ek baar kuch chala jaye toh wapas aana mushkil ho jata hai? ...Aapka muh, jab samne free ka khana ho! Happy Proposal Day! 🍕👄",
                "Riddle: Wo kya hai jo din bhar khula rahe toh aafat, aur band rahe toh duniya me aman-shanti? ...Karishma ji ka muh! Happy Anniversary! 🤐🚨",
                "Bataiye: Aisi kaun si cheez hai jo size me choti hai par pura ghar sar par utha sakti hai? ...Aapka wo masoom sa muh! 📢😂",
                "Ek sawaal: Wo kya hai jo 23 November ko band ho jana chahiye tha par tab se aur tez chal raha hai? ...Aapki zubaan! Happy $proposalYears Year${if (proposalYears > 1) "s" else ""}! 🏃♀️💨",
                "Paheli: Aisi kaun si cheez hai jo pehle bohot meethi lagti hai, fir dheere dheere toxic ho jaati hai? ...Aapki 'Achha suniye na' wali baatein! 🧪😈",
                "Riddle: Wo kya hai jo gusse me laal hota hai, paani mangta hai aur meri jaan le sakta hai? ...Aapka muh jab aapko bhookh lagi ho! 🌶️💀",
                "Bataiye kaun si cheez hai jo lene se pehle sochna padta hai par andar jaate hi aawaz nikal jaati hai? ...Humara gussa aur aapka lamba lecture! 🤷♂️😂",
                "Riddle: Wo kaun si jagah hai jahan sach bolne par seedha maut likhi hai? ...Aapke muh ke bilkul samne! Happy Proposal Day Karishma ji! 💀🙏",

                // Dark Humor & Sweet Poison
                "23 November 2025 ko humne apni maut ke parwane par khud sign kiya tha... aur wo khoobsurat parwana aap thin! Happy Proposal Anniversary! ⚰️💍",
                "Log marne ke baad nark me jaate hain, humne 23 November ko zinda rehte hue hi apna personal yamaraj chun liya tha! 👹❤️",
                "Happy Anniversary Karishma ji! Aapke sath pyaar kisi horror movie ke pehle 15 minute jaisa hai—shuru me kitna peaceful lagta hai sab! 🎬👻",
                "Agar aap kisi din humara murder bhi kar dein na, toh police ko boliyega 'isne muh chalane ko bola tha'! Case wahi khatam! 🔪😂",
                "23 November: The day I traded my mental peace for your non-stop high-voltage drama. Phir bhi pyaar aap hi se hai! 🧠📉❤️",
                "Kabhi-kabhi lagta hai 23 November ko proposal accept karne se pehle ek life insurance policy le leni chahiye thi! 📑💀",
                "Aapka pyaar us cyanide jaisa hai jo dheere-dheere asar karta hai par taste me bohot meetha lagta hai! 🧪🍬",
                "Happy Proposal Day! Aapke sath reh kar seekh liya hai ki darr ke aage jeet nahi, seedha aapka gussa hota hai! 🥶💥",

                // Teasing, Banter & "Muh Phulana"
                "23 November 2025 se lekar aaj tak, aadhi zindagi aapka phoola hua muh theek karne me hi nikal gayi! 🎈😤",
                "Bataiye aisi kaun si machine hai jo bina petrol ke 24 ghante chal sakti hai? ...Aapka muh jab humse behes karni ho! ⛽📢",
                "Happy Anniversary! Uss din 'Haan' bola tha aapne, ya humari poori azaadi par permanent lock lagaya tha? 🔒🤐",
                "Aapke is chhote se muh se itni khatarnak dhamkiyan kaise nikal aati hain Karishma ji? Research ka topic hai ye! 🔬🐒",
                "Agar muh phulane ka koi Olympic game hota, toh pakka gold medal aapke gale me latak raha hota! 🥇😤",
                "23 November ko aapne dil maanga tha, par roz subah humara dimaag chaba kar naashta karti hain aap! 🧠🍳",
                "Happy Proposal Day to the girl jiska muh shaant karwane ka bas ek hi rasta hai... ya toh khana ya humari kisses! 🍫💋",
                "Aapke muh se 'Mujhe kuch nahi chahiye' sunna is duniya ka sabse costly scam hai! ⚠️💸",

                // Double Meaning, Cheeky & Bold
                "Aapko lagta hai sirf phone garam hota hai? 23 November ke baad se aapka temperature bhi bina reason high rehta hai! 🔥😏",
                "Propose karte waqt kitni shareef lag rahi thin aap, baad me samajh aaya kitna dangerous virus download kar liya humne! 🦠💻",
                "Aapka ye jo teekha andaaz hai na, seedha dil pe nahi... thoda aur gehra hit karta hai! 🎯🌶️",
                "Happy Proposal Anniversary! Aapke aage humari halat aisi ho jaati hai jaise principal ke cabin me khada masoom bacha! 🧍♂️💀",
                "23 November ko haath kya thama aapka, aapne toh humari poori existence par copyright laga diya! 📑🚫",
                "Aapke nakhre jhelna aur 440 volt ki taar chhoona... dono me shock barabar ka lagta hai! ⚡😉",
                "Riddle: Wo kya hai jo raat me lamba hota hai aur subah hote hi chhota ho jata hai? ...Aapka patience! Happy Anniversary! ⏳😴",
                "Log kehte hain chup rehna ek art hai... par aap is art me sabse badi zero hain Karishma ji! 🎨😂",

                // Punchy, Edgy & Relatable
                "23 November 2025: The historic day jab hum officially aapka permanent bakra ban gaye! 🐐💍",
                "Happy Proposal Day! Aapke is pyaare se muh se jo aag nikalti hai na, usse pura Gorakhpur garam ho sakta hai! 🌋🔥",
                "Aapko jhelna aasan kaam nahi hai Karishma, par humari kismat dekhiye... hume easy cheezein pasand hi nahi aatin! 😎❤️",
                "Ek haath me phool aur doosre me zahar ka pyala le kar khade the... aapne dono chheen liye! 🍷🥀",
                "Happy Anniversary Karishma ji! Chahe kitna bhi muh bana lijiye, aana toh ghoom-fir kar humare hi paas padega aapko! 🪃❤️",
                "23 November ko laga tha lottery lagi hai humari... ab samajh aa raha hai bumper prize par tax kitna lagta hai! 💸🎰",
                "Humara dil toh aapne 23 November ko le hi liya tha, bas dua hai humara liver aur kidney safe rahein! 🫀💀",
                "Happy Proposal Anniversary to my favourite headache! Aap jaisi bhi hain, bas humari hi rahiyega hamesha. ☠️💍❤️",

                // Riddles & Teases (Pehle Dimag Chalao, Fir Smile Karo)
                "Ek paheli solve kijiye: Aisi kaun si cheez hai jo pehle bohot tight thi, par ab dheeli ho gayi? ...Humari sharam aur formality! Happy Proposal Day! 😜💍",
                "Riddle: Wo kaun si cheez hai jo 23 November ko ghutne par baith kar di gayi thi aur aaj tak wahi phasi hui hai? ...Humara dil (aur humara chain-o-sukoon)! 😂❤️",
                "Bataiye: Aisi kaun si cheez hai jo aap roz subah se raat tak chaba sakti hain bina thake? ...Humara dimaag! Happy Anniversary Karishma ji! 🧠🍴",
                "Ek sawaal: Wo kya hai jo hum dono ke beech me roz raat ko aata hai aur bina charging ke chalta nahi? ...Phone ki screen! Happy Proposal Day! 📱🔥",
                "Paheli: Aisi kaun si cheez hai jo lene me thodi hesitation hoti hai, par andar aate hi chehre par glow aa jata hai? ...Humara pyaar bhara hug! Ganda mat sochiye! 🙈🫂",
                "Bataiye kaun si cheez pehle bohot choti lagti thi, par time ke sath badi hoti chali gayi? ...Aapke nakhre aur humara pyaar! 📈❤️",
                "Riddle: Wo kya hai jo 23 November 2025 ko shuru hua, raat me lamba chalta hai aur subah hote hi thaka deta hai? ...Humari late-night talks! 🥱💬",
                "Bataiye: Aisi kaun si cheez hai jo aap har ladai me bahar nikaal leti hain? ...Humari 6 mahine puraani galtiyan! Happy Anniversary! 📂😂",
                "Ek riddle: Wo kaun sa button hai jisko dabane se sound bohot loud nikalta hai? ...Karishma ji ka gussa button! 🚨😤",
                "Bataiye aisi kaun si cheez hai jo jitni zyada do, utni hi aur maangne ka mann karta hai? ...Humari kisses! Happy Proposal Anniversary! 💋✨",

                // Double Meaning, Cheeky & Flirty
                "Aapko lagta hai sirf battery down hoti hai? 23 November ke baad se humari resistance bhi aapke aage zero ho chuki hai! ⚡😏",
                "Proposal ke time lag raha tha bohot innocent hain aap... ab jaakar pata chala ki aapka engine kitna high performance hai! 🏎️💨",
                "Aisi nazar se mat dekhiye Karishma ji, humari niyat already borderline par rehti hai jab aap paas hoti hain! 🔥🙈",
                "23 November ko toh bas haath manga tha, par aapne toh full-body possession hi le liya humara! 👻❤️",
                "Aapko handle karna kisi high-voltage wire ko chhoone jaisa hai... shock bhi lagta hai aur maza bhi aata hai! ⚡😉",
                "Happy Proposal Anniversary! Aapka attitude itna garam hai ki winters me heater ki zaroorat hi nahi padti! ☕🔥",
                "Log bolte hain pyaar andha hota hai... par aapke sath reh kar samajh aaya ki pyaar thoda behra aur poora besharam bhi hota hai! 😂💍",
                "23 November ko 'Yes' bol kar aapne humari azaadi lock kar di, aur chaabi apne paas chhupa li! 🔒🗝️",
                "Humein soft cheezein pasand hain, par jab aap humare kandhe par sar rakhti hain na, tab dil sabse zyada melt hota hai. 🫠🤍",
                "Aapki cuteness ka dosage thoda zyada hi dangerous ho gaya hai Karishma ji, heart rate seedha double ho jaati hai! 💓📈",

                // Romantic & Proposal Vibe (Thoda Filmy, Thoda Bold)
                "23 November ko humne drop kiya tha apna single status, aur aapne seedha catch kar liya! Best catch of my life! 🎯💍",
                "Aap humari wahi favourite mistake hain jisko har janam me baar-baar karne ko tayaar hain! 🔁❤️",
                "Log ring pehnate hain ungli me, aapne toh seedha gale ka fanda daal diya... par sukoon bohot hai is fande me! 🪢😂❤️",
                if (proposalYears >= 1)
                    "Happy $proposalYears${getOrdinalSuffix(proposalYears)} Proposal Day! Ek aapka nasha hai jo $proposalYears saal baad bhi utarne ka naam nahi le raha! 🍷✨"
                else
                    "Happy Proposal Day! Ek aapka nasha hai jo utarne ka naam nahi le raha! 🍷✨",
                "23 November was the day when I officially licensed you to annoy me for the rest of eternity! 📝😎",
                "Aapka gussa aur humara patience... dono me race lagti hai aur jeet hamesha aapki zidd ki hoti hai! 🏁🏎️",
                "Humne toh bas dil diya tha, aapne toh sath me humari neend, chain aur phone ka password sab le liya! 📱🔓",
                "Happy Anniversary Karishma ji! Har saal aap thodi aur bold, thodi aur gorgeous, aur thodi aur khatarnak hoti ja rahi hain! 💃🔥",
                "23 November 2025 ko jo deal sign hui thi, usme lifetime delivery guaranteed hai... full entertainment ke sath! 📦🎉",
                "Agar pyaar karna jurm hota na, toh hum dono ko ab tak lifetime imprisonment mil chuki hoti ek hi room me! 🏰🗝️",

                // Late Night & Relatable Banter
                "Paheli: Wo kya hai jo bed par padte hi pehle nikaal kar phenka jata hai? ...Din bhar ka stress aur thakan! Dimaag saaf rakhiye! 🛌😂",
                "Happy Proposal Anniversary! 23 November se lekar aaj tak humne jitna argue kiya hai, utna toh lawyers bhi nahi karte! ⚖️❤️",
                "Aapki aankhon me kuch toh magnet laga hai, jab bhi dekhte hain dimaag ka fuse ud jata hai! 🧲👀",
                "23 November ko humara proposal accept hua tha, ya humari daal roti ka budget bigad gaya tha? Dono ek sath! 💸🍛",
                "Bataiye aisi kaun si jagah hai jahan jaana bohot aasan hai par nikalna na-mumkin? ...Karishma ji ke dil ka chakravyuh! 🌀❤️",
                "Aap humari private property hain, trespassing par seedha dande padenge baakiyon ko! 🚧🚫❤️",
                "Happy Proposal Day! Aapke aage humari koi acting nahi chalti, seedha surrender ho jaate hain! 🏳️🤍",
                "23 November ko 'Haan' bolte waqt socha tha itna bada package milega aapko? (Trouble aur Romance dono included!) 📦😜",
                "Aapke sath har din aisa lagta hai jaise kisi romantic comedy movie ka climax chal raha ho! 🎬🍿",
                "Riddle: Wo kya hai jo raat me khada rehta hai aur subah gir jata hai? ...Humara late-night sleeping schedule! Happy Anniversary! 🌙🥱",

                // Sharp, Punchy & Spicy
                "Happy Proposal Anniversary meri jaan! Aapka nasha whiskey se bhi zyada strong hai! 🥃🔥",
                "23 November ko dil churaya tha aapne, ab poora aadmi hi kidnap kar liya hai! 🚨❤️",
                "Aap thodi kam teekhi raha kijiye Karishma ji, already humara temperature high rehta hai! 🌶️🥵",
                "Ek hi dil tha jo 23 November ko de diya... kidney maangengi toh sochna padega! 🫘😂",
                "Happy Anniversary! Officially hooked, booked, and completely cooked by you! 🍳💍",
                "Aapke sath time itna fast bhaagta hai jaise salary aane ke baad account balance! ⏳💸",
                "23 November 2025: The day our life went from black-and-white to full-on HD spicy romance! 🌈🔥",
                "Riddle: Wo kya hai jo aapke paas aate hi humari body me freeze ho jata hai? ...Humara dimaag! 🧊🧠❤️",
                "Aap sirf dil me nahi rehtin Karishma ji, seedha humari ragon me flow karti hain! 🩸✨",
                "Happy Proposal Anniversary! Jitna bhi pareshaan kar lijiye, end me aana toh humare hi paas hai aapko! 🫂💍❤️",

                // The Moment, Nervousness & That "YES"
                "23 November 2025... us din propose karne se pehle humari saansein ruki hui thin! Happy Proposal Anniversary Karishma ji! 💍❤️",
                "Jab aapne 'Haan' bola tha na, aisa laga jaise humari poori kismat ek second me badal gayi ho! 🥺✨",
                "23 November ko humara dil haath me tha aur dimaag me bas aap. Thank you for choosing me Karishma ji! 🕊️💖",
                "Wo pal jab humne aapka haath pakad kar apne dil ki baat boli thi... aaj bhi sochte hain toh rongte khade ho jaate hain. 🤝💍",
                "Zindagi me bohot faisle liye hain, par 23 November 2025 ko aapko propose karna humari life ka sabse best decision tha! 🌟❤️",
                "Aapka wo sharma kar 'Haan' kehna humare kaano me bajne wala sabse meetha geet hai. Happy Anniversary meri jaan! 🎶🤍",
                "23 November ko humari duniya ek insaan ke aas-paas aakar thehar gayi thi—aur wo aap hain Karishma ji! 🌍💍",
                "Us din humare paas bohot shabdh nahi the, bas ek sachha dil tha jo poori tarah aapka ho chuka tha. 🤍✨",
                "Aapne sirf proposal accept nahi kiya tha, aapne humari aane wali poori zindagi ko khoobsurat bana diya tha! 💐❤️",
                "23 November 2025 was the exact moment when our dreams turned into reality. Happy Proposal Day! 🪄💍",

                // Cute, Romantic & Forever Promises
                if (proposalYears >= 1)
                    "Happy $proposalYears${getOrdinalSuffix(proposalYears)} Proposal Anniversary Karishma ji! Wada hai har saal aapko aur zyada pyaar karenge. 💍♾️"
                else
                    "Proposal Anniversary mubarak ho Karishma ji! Wada hai har saal aapko aur zyada pyaar karenge. 💍♾️",
                "23 November ko jo promise kiya tha sath nibhane ka, wo har guzarte din ke sath aur pakka hota ja raha hai. 🤝✨",
                "Aap humari girlfriend baad me baneen, pehle humari aatma ka sukoon ban gayi thin. Happy Proposal Day! 🕊️❤️",
                "Uss din aapne humara haath thama tha, aur humne decide kar liya tha ki ab ye haath aakhri saans tak nahi chhodna! 💍🤍",
                "Ek ring ya ek sawaal nahi tha wo, wo humari poori life aapke naam karne ka tareeka tha. Love you Karishma ji! 🌹✨",
                "23 November ke baad se humari har dua me sirf aur sirf hum dono ki khushiyan hoti hain. 🤲💖",
                "Aap se pyaar karna humari choice thi, par aapko propose karna humari kismat ka likha tha! 💍💫",
                "Happy Proposal Anniversary! Aap sirf humari partner nahi, humari poori universe hain Karishma ji. 🌌❤️",
                "Chahe hum kitne bhi budhe ho jayein, 23 November ka din humein hamesha wahi nervous bana dega! 🙈💍",
                "Us din aapne humari zindagi me aane ke liye 'Yes' kaha tha, aur hum roz upar wale ko 'Thank you' kehte hain! 🌸🤍",

                // Playful, Teasing & Fun Vibes
                "23 November 2025 ko aapne 'Haan' toh bol diya, ab lifetime warranty ke sath jhelna padega humein! 😜💍",
                "Happy Proposal Anniversary! Aaj tak samajh nahi aaya ki aapne humare me kya dekh kar haan bol diya tha? Par jo bhi tha, lottery lag gayi humari! 🎰😂",
                "Us din ke baad se aap officially humari life ki boss ban chuki hain! Happy Anniversary my cute boss! 👑❤️",
                "23 November ko offer accept kiya tha aapne, ab no return, no exchange policy laagu hai Karishma ji! 📜🚫😂",
                "Propose karte waqt humare haath kaanp rahe the, aur aap mast smile kar rahi thin jaise aapko pehle se pata tha! 😏💍",
                "Happy Proposal Anniversary! Ek baar 'Haan' bolkar kitne bade jhanjhat me phas gayi hain aap, realize ho raha hai ab? 🤷♂️😂❤️",
                "Uss din humne apna dil aapke haath me diya tha, aur aapne tab se lekar aaj tak uspe poora kabza kar rakha hai! 🏰❤️",
                "23 November: The day you officially signed up for my bad jokes and unlimited tantrums! Happy Anniversary Karishma ji! 📝😂",
                "Happy Proposal Day! Aapne humein select karke apna taste kaafi solid prove kar diya tha Karishma ji! 😎💍",
                "Kaash us din humne aapki 'Haan' ka voice note record kar liya hota, jab bhi ladti hain toh play karke sunate! 🎙️😜",

                // Deep, Emotional & Soul Connection
                "23 November 2025 ke baad se humari zindagi me koi bhatakna nahi bacha, humein humari manzil mil gayi. 🏡🤍",
                "Propose karna bas ek formality thi Karishma ji, humara dil toh bohot pehle hi aapka ho chuka tha. 💍✨",
                "Har saal 23 November ko bas ek hi baat yaad aati hai—ki khuda hum par kitna meherbaan raha hai. 🕊️❤️",
                "Aapka haath humare haath me hona duniya ki sabse badi taqat lagta hai. Happy Proposal Anniversary! 🤝💫",
                "Zindagi ke har mod par, har ladai ke baad, hum hamesha wahi chahenge jo humne 23 November ko chaha tha—sirf aap. 🤍💍",
                "Humari kahani bohot simple hai, par 23 November 2025 ne usme hamesha ke liye jaan daal di. 📖💖",
                "Ek pal ke liye laga tha agar aapne mana kar diya toh kya hoga... par aapki muskaan ne humari saari fikar mita di! 🥺🌸",
                "Happy Proposal Day Karishma ji. Aapka humare sath hona humari life ki sabse badi blessing hai. 🤲✨",
                "Wo 23rd November ka din humare rishte ki buniyaad tha, aur ye imarat humesha mazboot rahegi. 🏛️❤️",
                "Hum har janam me, har dafa, har duniya me 23 November ko aakar aapko hi propose karenge. ♾️💍",

                // Crisp, Loving & High-Impact
                "23 November 2025: The best day of our entire life. Happy Proposal Anniversary Karishma ji! 💍❤️",
                "You said YES, and everything in my life fell into the right place. Forever yours! 🥂✨",
                "Pehli baar aapka haath thama tha us din... aur aaj tak chhodne ka mann nahi kiya! 🤝💖",
                "Happy Proposal Day to my forever! I love you more than words could ever type. 💍🤍",
                "23 November ko shuru hua ye wada hum dono ke aakhri saans tak sath chalega. 🌹✨",
                "Still getting butterflies thinking about 23rd November 2025! Happy Anniversary Karishma ji! 🦋💍",
                "Best proposal, best decision, and the absolute best girl in the world! 🌟❤️",
                "Us din ki har ek second humare dil me hamesha ke liye freeze ho chuki hai. Love you so much Karishma ji! 🧊❤️",
                "Happy Proposal Anniversary! Thank you for choosing to walk this life with me. 🚶♂️🚶♀️💍",
                "23 November 2025 will forever be my lucky date. Happy Anniversary meri jaan Karishma ji! 🍀💍❤️"
            )
        )

        return listOf(birthdayDate, firstTalkDate, proposalDate)
    }

    private fun getOrdinalSuffix(n: Int): String {
        return when {
            n in 11..13 -> "th"
            n % 10 == 1 -> "st"
            n % 10 == 2 -> "nd"
            n % 10 == 3 -> "rd"
            else -> "th"
        }
    }

    /**
     * Checks if today matches any configured special date (21 March, 1 July, 23 November).
     * STRICT: Only returns a SpecialDateInfo if today's calendar date is GENUINELY
     * that exact special date. Returns null on all other days.
     */
    fun getTodaySpecialDate(calendar: Calendar = Calendar.getInstance(), allowDemoIfActive: Boolean = false): SpecialDateInfo? {
        val currentMonth = calendar.get(Calendar.MONTH)
        val currentDay = calendar.get(Calendar.DAY_OF_MONTH)

        val dates = getSpecialDates(calendar)
        return dates.find { it.month == currentMonth && it.dayOfMonth == currentDay }
    }

    /**
     * Retrieves the next unique, dynamic message for the given special date.
     * Guarantees that every notification ("हर बारी नया वाला मैसेज") delivers a brand new,
     * fresh message from the 80+ curated messages for that date, cycling sequentially
     * without repeating until all messages have been shown.
     */
    fun getNextDynamicMessage(context: Context, dateInfo: SpecialDateInfo): String {
        val prefs = context.getSharedPreferences("soniclet_special_date_notif_tracker", Context.MODE_PRIVATE)
        val keyIndex = "msg_idx_${dateInfo.month}_${dateInfo.dayOfMonth}"
        val lastIndex = prefs.getInt(keyIndex, -1)
        val nextIndex = (lastIndex + 1) % dateInfo.messages.size

        prefs.edit()
            .putInt(keyIndex, nextIndex)
            .putLong("last_notif_time_${dateInfo.month}_${dateInfo.dayOfMonth}", System.currentTimeMillis())
            .apply()

        return dateInfo.messages[nextIndex]
    }

    /**
     * Peek at the current message for this date without advancing the rotation index.
     */
    fun peekCurrentMessage(context: Context, dateInfo: SpecialDateInfo): String {
        val prefs = context.getSharedPreferences("soniclet_special_date_notif_tracker", Context.MODE_PRIVATE)
        val keyIndex = "msg_idx_${dateInfo.month}_${dateInfo.dayOfMonth}"
        val currentIndex = prefs.getInt(keyIndex, 0)
        val safeIndex = if (currentIndex in dateInfo.messages.indices) currentIndex else 0
        return dateInfo.messages[safeIndex]
    }

    /**
     * Get a random love notification message for the given special date.
     */
    fun getRandomMessage(dateInfo: SpecialDateInfo): String {
        return dateInfo.messages.random()
    }

    /**
     * Returns all love messages across all categories with metadata.
     */
    fun getAllLoveMessages(calendar: Calendar = Calendar.getInstance()): List<MuhLoveMessage> {
        val dates = getSpecialDates(calendar)
        return dates.flatMap { dateInfo ->
            val dateLabel = when (dateInfo.month) {
                Calendar.MARCH -> "21 March"
                Calendar.JULY -> "1 July"
                Calendar.NOVEMBER -> "23 November"
                else -> ""
            }
            dateInfo.messages.map { msg ->
                MuhLoveMessage(
                    category = dateInfo.title,
                    dateLabel = dateLabel,
                    icon = dateInfo.celebrationIcon,
                    text = msg
                )
            }
        }
    }

    /**
     * Pick a random love note from all categories.
     */
    fun getRandomLoveMessage(calendar: Calendar = Calendar.getInstance()): MuhLoveMessage {
        val all = getAllLoveMessages(calendar)
        return if (all.isNotEmpty()) all.random() else MuhLoveMessage("Special Note", "", "💖", "Karishma ji, you are my forever! ❤️")
    }

    /**
     * Pick a daily consistent note (different each day of the year).
     */
    fun getDailyLoveMessage(calendar: Calendar = Calendar.getInstance()): MuhLoveMessage {
        val all = getAllLoveMessages(calendar)
        if (all.isEmpty()) return MuhLoveMessage("Special Note", "", "💖", "Karishma ji, you are my forever! ❤️")
        val dayIndex = calendar.get(Calendar.DAY_OF_YEAR) % all.size
        return all[dayIndex]
    }
}

data class MuhLoveMessage(
    val category: String,
    val dateLabel: String,
    val icon: String,
    val text: String
)
