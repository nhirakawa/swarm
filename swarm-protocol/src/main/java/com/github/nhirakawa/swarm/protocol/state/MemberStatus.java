package com.github.nhirakawa.swarm.protocol.state;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.As;
import com.github.nhirakawa.swarm.protocol.model.SwarmAddress;

@JsonTypeInfo(
  use = JsonTypeInfo.Id.NAME,
  property = "type",
  include = As.EXISTING_PROPERTY
)
@JsonSubTypes({
  @Type(name = "ALIVE", value = MemberStatus.Alive.class),
  @Type(name = "SUSPECTED", value = MemberStatus.Suspected.class),
  @Type(name = "CONFIRMED", value = MemberStatus.Confirmed.class)
})
public sealed interface MemberStatus permits MemberStatus.Alive, MemberStatus.Suspected, MemberStatus.Confirmed {
  SwarmAddress address();
  long incarnation();
  String type();

  MemberStatus merge(MemberStatus memberStatus);

  @JsonIgnore
  boolean isEligibleForPing();

  static MemberStatus alive(SwarmAddress address, long incarnation) {
    return new Alive(address, incarnation);
  }

  static MemberStatus suspected(SwarmAddress address, long incarnation) {
    return new Suspected(address, incarnation);
  }

  static MemberStatus confirmed(SwarmAddress address, long incarnation) {
    return new Confirmed(address, incarnation);
  }

  record Alive(SwarmAddress address, long incarnation) implements MemberStatus {
    @Override
    @JsonProperty("type")
    public String type() {
      return "ALIVE";
    }

    @Override
    public MemberStatus merge(MemberStatus memberStatus) {
      return switch (memberStatus) {
        case Alive(SwarmAddress ignored, long otherIncarnation) -> new Alive(address, Math.max(incarnation, otherIncarnation));
        case Suspected(SwarmAddress ignored, long otherIncarnation) -> {
          if (otherIncarnation >= incarnation) {
            yield memberStatus;
          } else {
            yield this;
          }
        }
        case Confirmed(SwarmAddress ignored1, long ignored2) -> memberStatus;
      };
    }

    @Override
    public boolean isEligibleForPing() {
      return true;
    }
  }

  record Suspected(SwarmAddress address, long incarnation) implements MemberStatus{
    @Override
    @JsonProperty("type")
    public String type() {
      return "SUSPECTED";
    }

    @Override
    public MemberStatus merge(MemberStatus memberStatus) {
      return switch (memberStatus) {
        case Alive(SwarmAddress ignored, long otherIncarnation) -> {
          if (otherIncarnation >= incarnation) {
            yield memberStatus;
          } else {
            yield this;
          }
        }
        case Suspected(SwarmAddress ignored, long otherIncarnation) -> new Suspected(address, Math.max(incarnation, otherIncarnation));
        case Confirmed(SwarmAddress ignored1, long ignored2) -> memberStatus;
      };
    }

    @Override
    public boolean isEligibleForPing() {
      return true;
    }
  }

  record Confirmed(SwarmAddress address, long incarnation) implements MemberStatus {
    @Override
    @JsonProperty("type")
    public String type() {
      return "CONFIRMED";
    }

    @Override
    public MemberStatus merge(MemberStatus memberStatus) {
      if (memberStatus instanceof Confirmed(SwarmAddress ignored, long otherIncarnation)) {
        return new Confirmed(address, Math.max(incarnation, otherIncarnation));
      } else {
        return this;
      }
    }

    @Override
    public boolean isEligibleForPing() {
      return false;
    }
  }
}
