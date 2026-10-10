package com.veylon.save;

import com.veylon.Game;
import com.veylon.item.Inventory;
import com.veylon.util.Vec3i;
import com.veylon.world.BlockType;
import com.veylon.world.Chunk;
import com.veylon.world.Fixtures;
import com.veylon.world.urban.UrbanContainers;
import com.veylon.world.urban.UrbanIds;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;

/**
 * urban.containers v1: 20-byte header then fixed 36-byte rows, at most 32,768.
 * Inventory bytes remain solely in the frozen core crate map; each live row
 * declares whether that authoritative inventory exists. No second item copy.
 */
final class UrbanContainersSection {
    static final String ID="urban.containers";
    private static final int VERSION=1;
    private static final int ROW_BYTES=36;
    private UrbanContainersSection() { }

    static byte[] write(Game game) throws IOException {
        var state=game.world.urban.containers;
        ByteArrayOutputStream bytes=new ByteArrayOutputStream(20+state.size()*ROW_BYTES);
        DataOutputStream out=new DataOutputStream(bytes);
        out.writeInt(VERSION); out.writeInt(game.world.urban.contentRevision());
        out.writeLong(state.nextPlayerId()); out.writeInt(state.size());
        for (var entry : state.entries().stream().sorted(Comparator.comparingLong(e -> e.id)).toList()) {
            validateIdentity(game,entry);
            boolean contents=!entry.state.retired() && game.world.crateContents.containsKey(entry.position);
            validateInventory(game,entry,contents);
            out.writeLong(entry.id); SaveSystem.writeVec(out,entry.position);
            out.writeByte(entry.block.id()); out.writeByte(entry.owner.code); out.writeLong(entry.ownerId);
            out.writeByte(entry.state.code); out.writeFloat(entry.progress); out.writeBoolean(contents);
        }
        out.flush(); return bytes.toByteArray();
    }

    static void read(byte[] payload, Game game) throws IOException {
        if (!UrbanContainers.manages(game.world)) { throw new IOException(ID+" requires generated interiors"); }
        DataInputStream in=new DataInputStream(new ByteArrayInputStream(payload));
        if (in.available()<20 || in.readInt()!=VERSION) { throw new IOException("unsupported or truncated "+ID); }
        int content=in.readInt();
        if (content!=game.world.urban.contentRevision()) { throw new IOException("container content revision mismatch"); }
        long next=in.readLong(); int count=in.readInt();
        if (count<0 || count>UrbanContainers.MAX_ENTRIES || (long) count*ROW_BYTES!=in.available()) {
            throw new IOException("invalid "+ID+" count or length");
        }
        var state=game.world.urban.containers;
        Set<Vec3i> livePositions=new HashSet<>();
        try {
            for (int i=0;i<count;i++) {
                long id=in.readLong(); Vec3i position=SaveSystem.readVec(in);
                BlockType block=BlockType.byId(in.readByte());
                var owner=UrbanContainers.Owner.fromCode(in.readUnsignedByte()); long ownerId=in.readLong();
                var status=UrbanContainers.State.fromCode(in.readUnsignedByte()); float progress=in.readFloat();
                int contentsFlag=in.readUnsignedByte();
                if (contentsFlag>1) { throw new IOException("invalid container inventory flag"); }
                var entry=new UrbanContainers.Entry(id,position,block,owner,ownerId,status,progress);
                validateIdentity(game,entry);
                if (!status.retired()) {
                    if (!livePositions.add(position)) { throw new IOException("duplicate active container cell"); }
                    game.world.getOrCreateChunk(Math.floorDiv(position.x(),16),Math.floorDiv(position.z(),16));
                    if (game.world.getBlock(position.x(),position.y(),position.z())!=block) {
                        throw new IOException("container fixture no longer matches its live state");
                    }
                }
                validateInventory(game,entry,contentsFlag==1);
                state.restore(entry);
            }
            state.restoreNextPlayerId(next);
        } catch (IllegalArgumentException invalid) { throw new IOException("invalid "+ID,invalid); }
        for (Vec3i position : game.world.crateContents.keySet()) {
            if (state.at(position)==null) { throw new IOException("unbound urban container inventory"); }
        }
    }

    private static void validateIdentity(Game game, UrbanContainers.Entry entry) throws IOException {
        if (entry.position.y()<0 || entry.position.y()>=Chunk.SY || !Fixtures.isContainer(entry.block)
                || !Float.isFinite(entry.progress) || entry.progress<0 || entry.progress>=4
                || entry.state!=UrbanContainers.State.SEARCHING && entry.progress!=0) {
            throw new IOException("invalid container cell, fixture or search progress");
        }
        if (entry.id<0) {
            if (entry.owner!=UrbanContainers.Owner.PLAYER || entry.ownerId!=UrbanIds.NONE
                    || !entry.state.retired() && !entry.state.revealed()) {
                throw new IOException("invalid player storage identity");
            }
        } else {
            if (!UrbanIds.isValid(entry.id) || UrbanIds.kind(entry.id)!=UrbanIds.Kind.FIXTURE) {
                throw new IOException("invalid generated container id");
            }
            var plan=UrbanContainers.planned(game.world,entry.position);
            boolean occupied=plan!=null && plan.building().variant()==com.veylon.world.urban.BuildingPlan.Variant.OCCUPIED;
            if (plan==null || plan.fixture().id()!=entry.id || plan.fixture().block()!=entry.block
                    || entry.owner==UrbanContainers.Owner.PLAYER
                    || (entry.owner==UrbanContainers.Owner.SURVIVOR)!=occupied
                    || entry.owner==UrbanContainers.Owner.SURVIVOR && entry.ownerId!=plan.building().id()
                    || entry.owner==UrbanContainers.Owner.ABANDONED && entry.ownerId!=UrbanIds.NONE
                    || entry.state==UrbanContainers.State.SEARCHING
                    && entry.progress>=com.veylon.world.urban.LootTables.searchSeconds(plan.table())) {
                throw new IOException("container identity, owner or progress mismatch");
            }
        }
    }

    private static void validateInventory(Game game, UrbanContainers.Entry entry, boolean declared) throws IOException {
        Inventory inventory=entry.state.retired() ? null : game.world.crateContents.get(entry.position);
        if (declared!=(inventory!=null) || entry.state.retired() && declared
                || entry.state==UrbanContainers.State.EMPTY && inventory!=null
                && (game.world.urban.contentRevision()!=1 || !inventory.isEmpty())
                || entry.state==UrbanContainers.State.SEARCHED && inventory==null) {
            throw new IOException("container inventory reference mismatch");
        }
        if (inventory==null) { return; }
        if (inventory.size()!=UrbanContainers.SLOTS) { throw new IOException("invalid urban inventory capacity"); }
        for (int i=0;i<inventory.size();i++) {
            var stack=inventory.get(i);
            if (stack==null) { continue; }
            var weapon=com.veylon.combat.WeaponRegistry.of(stack.type);
            if (stack.count<=0 || stack.count>stack.type.maxStack || !Float.isFinite(stack.durability)
                    || !Float.isFinite(stack.freshness) || stack.durability< -1
                    || stack.durability>stack.type.maxDurability || stack.freshness< -1
                    || stack.freshness>stack.type.spoilSeconds || stack.charge<0
                    || stack.charge>(weapon==null ? 0 : weapon.magazine)) {
                throw new IOException("invalid urban inventory stack metadata");
            }
        }
    }

    static void finishLoad(Game game) throws IOException {
        if (!UrbanContainers.manages(game.world) || game.world.urban.containers.sectionRestored()) { return; }
        if (game.world.urban.contentRevision()!=1) { throw new IOException("missing required "+ID); }
        try { game.world.urban.containers.adoptLegacy(game.world); }
        catch (IllegalArgumentException invalid) { throw new IOException("invalid legacy container state",invalid); }
    }
}
