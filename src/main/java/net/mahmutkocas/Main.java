package net.mahmutkocas;

import discord4j.core.DiscordClient;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.guild.MemberJoinEvent;
import discord4j.core.event.domain.guild.MemberUpdateEvent;
import discord4j.core.event.domain.lifecycle.ReadyEvent;
import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.object.entity.Guild;
import discord4j.core.object.entity.Member;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.User;
import discord4j.core.object.presence.Activity;
import discord4j.core.object.presence.ClientActivity;
import discord4j.core.object.presence.ClientPresence;
import discord4j.core.object.presence.Status;
import discord4j.core.object.reaction.ReactionEmoji;
import discord4j.core.spec.GuildMemberEditSpec;
import discord4j.gateway.intent.Intent;
import discord4j.gateway.intent.IntentSet;
import reactor.core.publisher.Mono;
import reactor.util.annotation.Nullable;

import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

public class Main {

    public static final Long ADMIN_ID = 218356083704463363L;
    public static AtomicReference<String> targetNickname = new AtomicReference<>("Selektör");
    public static final String COMMAND_START = "Artık Oğulcan";
    public static final String COMMAND_END = "bilinsin";

    public static final String COMMAND_RESET = "Artık Oğulcan özüne dönsün";

    public static final Locale LOCALE_TR = new Locale("TR-tr");

    public static void main(String[] args) {
        NicknameScheduler nicknameScheduler = new NicknameScheduler();
        nicknameScheduler.start();
        DiscordClient dc = DiscordClient.create("MTE1NDUwODcxMTU4NzQ3OTYwMg.Gc0Eni.72HxzuSkI7E-5QXyAGwF2jUlM7gJMTTycZ3YvY");

        Mono<Void> login = dc
                .gateway()
                .setInitialPresence(shardInfo -> {
                    ClientActivity activity = ClientActivity.of(Activity.Type.WATCHING, "Rekter", "https://www.youtube.com/MahmutKocas");
                    return ClientPresence.of(Status.DO_NOT_DISTURB, activity);
                })
                .setEnabledIntents(IntentSet.of(Intent.GUILD_MEMBERS, Intent.GUILD_MESSAGES))
                .withGateway((GatewayDiscordClient client) -> {
                    Mono<Void> ready = client.on(ReadyEvent.class, event -> Mono.fromRunnable(() -> {
                        final User self = event.getSelf();
                        System.out.printf("Logged in as %s#%s%n", self.getUsername(), self.getDiscriminator());
                    })).then();

                    Mono<Void> join = client.on(MemberJoinEvent.class, memberJoinEvent -> updateRekter(memberJoinEvent.getMember())).then();

                    Mono<Void> update = client.on(MemberUpdateEvent.class, memberUpdateEvent -> updateRekter(memberUpdateEvent.getMember().block(Duration.ofSeconds(10)))).then();

                    Mono<Void> command = client.on(MessageCreateEvent.class, Main::processCommands).then();

                    return ready.and(join).and(update).and(command);
                });

        login.block();


    }


    private static Mono<Object> processCommands(MessageCreateEvent event) {
        return Mono.fromRunnable(() -> {
            Message message = event.getMessage();
            User user = message.getAuthor().orElse(null);

            String cmdStart = COMMAND_START.toLowerCase(LOCALE_TR);
            String cmdEnd = COMMAND_END.toLowerCase(LOCALE_TR);
            String cmdReset = COMMAND_RESET.toLowerCase(LOCALE_TR);
            String msg = message.getContent().toLowerCase(LOCALE_TR).trim();
            String realMsg = message.getContent();
            String targetNick;

            if(msg.startsWith(cmdStart) && msg.endsWith(cmdEnd)) {
                targetNick = realMsg.substring(cmdStart.length(), realMsg.length()-cmdEnd.length()).trim();
            } else if(msg.startsWith(cmdReset)) {
                targetNick = "Ben boş adamım";
            } else {
                return;
            }

            if(user == null || user.getId().asLong() != ADMIN_ID) {
                message.addReaction(ReactionEmoji.unicode("\uD83C\uDDF2"))
                        .then(message.addReaction(ReactionEmoji.unicode("🅰")))
                        .then(message.addReaction(ReactionEmoji.unicode("\uD83C\uDDF1")))
                        .block(Duration.ofSeconds(3));
                return;
            }
            targetNickname.set(targetNick);

            Guild guild = event.getGuild().block(Duration.ofSeconds(3));
            if(guild != null) {
                guild.getMembers().toIterable().forEach(member -> updateRekter(member).block(Duration.ofSeconds(1)));
            }

            message.addReaction(ReactionEmoji.unicode("👍")).block(Duration.ofSeconds(1));

            System.out.println("Updated targetNickname to " + targetNick);
        });
    }

    private static Mono<Object> updateRekter(@Nullable Member member) {
        return Mono.fromRunnable(() -> {
            if (member == null) {
                return;
            }
            String nickname = member.getNickname().orElse("");
            String username = member.getMemberData().user().username();
            if (nickname.equals(targetNickname.get())) {
                return;
            }
            long id = member.getMemberData().user().id().asLong();
            if (id == 950326170832998462L || username.toLowerCase().contains("rekter") || nickname.toLowerCase().contains("rekter")) {
                synchronized (NicknameScheduler.targetMemberList) {
                    System.out.println("Member caught nickname: " + nickname + " username: " + username + " id: " + id);
                    NicknameScheduler.targetMemberList.put(id, member);
                }
            }
        });
    }

    public static class NicknameScheduler extends Thread {

        public static final ConcurrentHashMap<Long, Member> targetMemberList = new ConcurrentHashMap<>();


        @Override
        public void run() {
            while (true) {
                try {
                    synchronized (targetMemberList) {
                        targetMemberList.values().forEach(member -> {
                            String nickname = member.getNickname().orElse("");
                            String username = member.getMemberData().user().username();
                            GuildMemberEditSpec editSpec = GuildMemberEditSpec.builder().nicknameOrNull(targetNickname.get()).build();
                            System.out.println("Processing nickname: " + nickname + " username: " + username);
                            member.edit(editSpec).block(Duration.ofSeconds(15));
                            System.out.println("Updated nickname: " + nickname + " username: " + username);
                        });
                        targetMemberList.clear();
                    }
                    Thread.sleep(2500);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}