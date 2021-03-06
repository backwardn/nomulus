// Copyright 2017 The Nomulus Authors. All Rights Reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package google.registry.model.reporting;

import static google.registry.util.CollectionUtils.nullToEmptyImmutableCopy;

import com.google.common.collect.ImmutableSet;
import com.googlecode.objectify.Key;
import com.googlecode.objectify.annotation.Entity;
import com.googlecode.objectify.annotation.Id;
import com.googlecode.objectify.annotation.IgnoreSave;
import com.googlecode.objectify.annotation.Index;
import com.googlecode.objectify.annotation.Parent;
import com.googlecode.objectify.condition.IfNull;
import google.registry.model.Buildable;
import google.registry.model.EppResource;
import google.registry.model.ImmutableObject;
import google.registry.model.annotations.ReportedOn;
import google.registry.model.domain.Period;
import google.registry.model.eppcommon.Trid;
import google.registry.persistence.VKey;
import java.util.Set;
import javax.annotation.Nullable;
import javax.persistence.AttributeOverride;
import javax.persistence.AttributeOverrides;
import javax.persistence.Column;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.MappedSuperclass;
import javax.persistence.SequenceGenerator;
import javax.persistence.Transient;
import org.joda.time.DateTime;

/** A record of an EPP command that mutated a resource. */
@ReportedOn
@Entity
@MappedSuperclass
public class HistoryEntry extends ImmutableObject implements Buildable {

  /** Represents the type of history entry. */
  public enum Type {
    CONTACT_CREATE,
    CONTACT_DELETE,
    CONTACT_DELETE_FAILURE,
    CONTACT_PENDING_DELETE,
    CONTACT_TRANSFER_APPROVE,
    CONTACT_TRANSFER_CANCEL,
    CONTACT_TRANSFER_REJECT,
    CONTACT_TRANSFER_REQUEST,
    CONTACT_UPDATE,
    /**
     * Used for history entries that were allocated as a result of a domain application.
     *
     * <p>Domain applications (and thus allocating from an application) no longer exist, but we have
     * existing domains in the system that were created via allocation and thus have history entries
     * of this type under them, so this is retained for legacy purposes.
     */
    @Deprecated
    DOMAIN_ALLOCATE,
    /**
     * Used for domain registration autorenews explicitly logged by {@link
     * google.registry.batch.ExpandRecurringBillingEventsAction}.
     */
    DOMAIN_AUTORENEW,
    DOMAIN_CREATE,
    DOMAIN_DELETE,
    DOMAIN_RENEW,
    DOMAIN_RESTORE,
    DOMAIN_TRANSFER_APPROVE,
    DOMAIN_TRANSFER_CANCEL,
    DOMAIN_TRANSFER_REJECT,
    DOMAIN_TRANSFER_REQUEST,
    DOMAIN_UPDATE,
    HOST_CREATE,
    HOST_DELETE,
    HOST_DELETE_FAILURE,
    HOST_PENDING_DELETE,
    HOST_UPDATE,
    /** Resource was created by an escrow file import. */
    RDE_IMPORT,
    /**
     * A synthetic history entry created by a tool or back-end migration script outside of the scope
     * of usual EPP flows. These are sometimes needed to serve as parents for billing events or poll
     * messages that otherwise wouldn't have a suitable parent.
     */
    SYNTHETIC
  }

  /** The autogenerated id of this event. */
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "HistorySequenceGenerator")
  @SequenceGenerator(
      name = "HistorySequenceGenerator",
      sequenceName = "history_id_sequence",
      allocationSize = 1)
  @Id
  @javax.persistence.Id
  @Column(name = "historyRevisionId")
  Long id;

  /** The resource this event mutated. */
  @Parent @Transient protected Key<? extends EppResource> parent;

  /** The type of history entry. */
  @Column(nullable = false, name = "historyType")
  @Enumerated(EnumType.STRING)
  Type type;

  /**
   * The length of time that a create, allocate, renewal, or transfer request was issued for. Will
   * be null for all other types.
   */
  @IgnoreSave(IfNull.class)
  @Transient // domain-specific
  Period period;

  /** The actual EPP xml of the command, stored as bytes to be agnostic of encoding. */
  @Column(nullable = false, name = "historyXmlBytes")
  byte[] xmlBytes;

  /** The time the command occurred, represented by the ofy transaction time. */
  @Index
  @Column(nullable = false, name = "historyModificationTime")
  DateTime modificationTime;

  /** The id of the registrar that sent the command. */
  @Index
  @Column(name = "historyRegistrarId")
  String clientId;

  /**
   * For transfers, the id of the other registrar.
   *
   * <p>For requests and cancels, the other registrar is the losing party (because the registrar
   * sending the EPP transfer command is the gaining party). For approves and rejects, the other
   * registrar is the gaining party.
   */
  @Transient // domain-specific
  String otherClientId;

  /** Transaction id that made this change, or null if the entry was not created by a flow. */
  @Nullable
  @AttributeOverrides({
    @AttributeOverride(
        name = "clientTransactionId",
        column = @Column(name = "historyClientTransactionId")),
    @AttributeOverride(
        name = "serverTransactionId",
        column = @Column(name = "historyServerTransactionId"))
  })
  Trid trid;

  /** Whether this change was created by a superuser. */
  @Column(nullable = false, name = "historyBySuperuser")
  boolean bySuperuser;

  /** Reason for the change. */
  @Column(nullable = false, name = "historyReason")
  String reason;

  /** Whether this change was requested by a registrar. */
  @Column(nullable = false, name = "historyRequestedByRegistrar")
  Boolean requestedByRegistrar;

  /**
   * Logging field for transaction reporting.
   *
   * <p>This will be empty for any HistoryEntry generated before this field was added. This will
   * also be empty if the HistoryEntry refers to an EPP mutation that does not affect domain
   * transaction counts (such as contact or host mutations).
   */
  @Transient // domain-specific
  Set<DomainTransactionRecord> domainTransactionRecords;

  public Key<? extends EppResource> getParent() {
    return parent;
  }

  public Type getType() {
    return type;
  }

  public Period getPeriod() {
    return period;
  }

  public byte[] getXmlBytes() {
    return xmlBytes;
  }

  public DateTime getModificationTime() {
    return modificationTime;
  }

  public String getClientId() {
    return clientId;
  }

  public String getOtherClientId() {
    return otherClientId;
  }

  /** Returns the TRID, which may be null if the entry was not created by a normal flow. */
  @Nullable
  public Trid getTrid() {
    return trid;
  }

  public boolean getBySuperuser() {
    return bySuperuser;
  }

  public String getReason() {
    return reason;
  }

  public Boolean getRequestedByRegistrar() {
    return requestedByRegistrar;
  }

  public ImmutableSet<DomainTransactionRecord> getDomainTransactionRecords() {
    return nullToEmptyImmutableCopy(domainTransactionRecords);
  }

  public static VKey<HistoryEntry> createVKey(Key<HistoryEntry> key) {
    // TODO(b/159207551): This will likely need some revision.  As it stands, this method was
    // introduced purely to facilitate testing of VKey specialization in VKeyTranslatorFactory.
    // This class will likely require that functionality, though perhaps not this implementation of
    // it.
    // For now, just assume that the primary key of a history entry is comprised of the parent
    // type, key and the object identifer.
    return VKey.create(
        HistoryEntry.class,
        key.getParent().getKind() + "/" + key.getParent().getName() + "/" + key.getId(),
        key);
  }

  @Override
  public Builder asBuilder() {
    return new Builder(clone(this));
  }

  /** A builder for {@link HistoryEntry} since it is immutable */
  public static class Builder<T extends HistoryEntry, B extends Builder<?, ?>>
      extends GenericBuilder<T, B> {
    public Builder() {}

    public Builder(T instance) {
      super(instance);
    }

    @Override
    public T build() {
      return super.build();
    }

    public B setParent(EppResource parent) {
      getInstance().parent = Key.create(parent);
      return thisCastToDerived();
    }

    // Until we move completely to SQL, override this in subclasses (e.g. HostHistory) to set VKeys
    public B setParent(Key<? extends EppResource> parent) {
      getInstance().parent = parent;
      return thisCastToDerived();
    }

    public B setType(Type type) {
      getInstance().type = type;
      return thisCastToDerived();
    }

    public B setPeriod(Period period) {
      getInstance().period = period;
      return thisCastToDerived();
    }

    public B setXmlBytes(byte[] xmlBytes) {
      getInstance().xmlBytes = xmlBytes;
      return thisCastToDerived();
    }

    public B setModificationTime(DateTime modificationTime) {
      getInstance().modificationTime = modificationTime;
      return thisCastToDerived();
    }

    public B setClientId(String clientId) {
      getInstance().clientId = clientId;
      return thisCastToDerived();
    }

    public B setOtherClientId(String otherClientId) {
      getInstance().otherClientId = otherClientId;
      return thisCastToDerived();
    }

    public B setTrid(Trid trid) {
      getInstance().trid = trid;
      return thisCastToDerived();
    }

    public B setBySuperuser(boolean bySuperuser) {
      getInstance().bySuperuser = bySuperuser;
      return thisCastToDerived();
    }

    public B setReason(String reason) {
      getInstance().reason = reason;
      return thisCastToDerived();
    }

    public B setRequestedByRegistrar(Boolean requestedByRegistrar) {
      getInstance().requestedByRegistrar = requestedByRegistrar;
      return thisCastToDerived();
    }

    public B setDomainTransactionRecords(
        ImmutableSet<DomainTransactionRecord> domainTransactionRecords) {
      getInstance().domainTransactionRecords = domainTransactionRecords;
      return thisCastToDerived();
    }
  }
}
